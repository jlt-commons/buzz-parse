(ns buzz-parse.html
  "HTML parsing over lexbor with crouton's API. `parse` and `parse-string`
  return a tree compatible with clojure.xml/parse: maps with :tag, :attrs and
  :content, text as strings. `select` (CSS selectors) and `parse-fragment` are
  lexbor extras."
  (:require [clojure.string :as str]
            [jolt.ffi :as ffi]
            [buzz-parse.ffi :as bp]))

;; DOM nodeType values
(def ^:private element 1)
(def ^:private text-node 3)
(def ^:private cdata 4)

;; jsoup keeps <script>/<style> contents verbatim (DataNode); other text goes
;; through TextNode.text(), which collapses whitespace runs to one space.
(def ^:private data-tags #{:script :style})

(defn- normalize-whitespace [s]
  (str/replace s #"[ \t\n\f\r]+" " "))

(defn- join-adjacent-strings [coll]
  (reduce (fn [acc x]
            (let [prev (peek acc)]
              (if (and (string? x) (string? prev))
                (conj (pop acc) (str prev x))
                (conj acc x))))
          []
          coll))

(defn- children [node]
  (->> (bp/first-child node)
       (iterate bp/next-node)
       (take-while (complement ffi/null?))))

(defn- attrs [node]
  (->> (bp/first-attr node)
       (iterate bp/next-attr)
       (take-while (complement ffi/null?))
       (map (fn [a] [(keyword (bp/attr-name a)) (bp/attr-value a)]))
       (into {})))

(declare ->clj)

(defn- content [node tag]
  (->> (children node)
       (keep #(->clj % tag))
       join-adjacent-strings
       not-empty))

(defn- ->clj
  "Node as Clojure data: an element map, a string, or nil (comments, doctypes,
  processing instructions). parent-tag picks data vs text handling."
  [node parent-tag]
  (let [t (bp/node-type node)]
    (cond
      (= t element)
      (let [tag (keyword (bp/tag-name node))]
        {:tag tag
         :attrs (not-empty (attrs node))
         :content (content node tag)})

      (or (= t text-node) (= t cdata))
      (let [s (bp/data node)]
        (if (data-tags parent-tag) s (normalize-whitespace s)))

      :else nil)))

(defn- with-document*
  [html f]
  (let [doc (bp/parse* html)]
    (when (ffi/null? doc)
      (throw (ex-info "lexbor failed to parse the document" {})))
    (try
      (f doc)
      (finally (bp/destroy doc)))))

(defn- document->clj [doc]
  (some #(when (= element (bp/node-type %)) (->clj % nil))
        (children (bp/root doc))))

(defn parse-string
  "Reads and parses the HTML from a supplied source text."
  [string]
  (with-document* string document->clj))

(defn parse
  "Reads and parses the HTML from the supplied source, which may be anything
  that can be consumed by clojure.core/slurp. Returns a tree compatible with
  that returned by clojure.xml/parse, i.e. a map that has the keys :tag, :attrs
  and :content."
  [source]
  (parse-string (slurp source)))

(defn parse-fragment
  "Parses an HTML fragment as the contents of a <body>, without synthesizing
  html/head/body. Returns a vector of the fragment's top-level nodes (element
  maps and strings), empty when there are none."
  [string]
  (with-document* ""
    (fn [doc]
      (let [root (bp/fragment* doc string)]
        (when (ffi/null? root)
          (throw (ex-info "lexbor failed to parse the fragment" {})))
        (or (content root :body) [])))))

(defn- take-string [p]
  (when (ffi/null? p)
    (throw (ex-info "lexbor serialization failed" {})))
  (try (ffi/ptr->string p) (finally (bp/free* p))))

(def ^:private renderers
  {:tree       #(->clj % nil)
   :html       #(take-string (bp/outer-html* %))
   :inner-html #(take-string (bp/inner-html* %))
   :text       #(take-string (bp/text* %))})

(defn select
  "Parses html (a string) and returns a vector of the nodes matching the CSS
  selector, in document order, each node once. opts:

    :as  :tree (default) element maps like parse returns
         :html        outer HTML of each match
         :inner-html  HTML of each match's children
         :text        text content of each match (whitespace kept)

  Throws ex-info for a selector lexbor can't parse."
  ([html selector] (select html selector {}))
  ([html selector {:keys [as] :or {as :tree}}]
   (let [render (or (renderers as)
                    (throw (ex-info (str "unknown :as " (pr-str as))
                                    {:as as :expected (set (keys renderers))})))]
     (with-document* html
       (fn [doc]
         (let [nodes (bp/select* (bp/root doc) selector)]
           (when (ffi/null? nodes)
             (throw (ex-info (str "invalid CSS selector: " selector)
                             {:selector selector})))
           (try
             (mapv #(render (bp/nodes-get nodes %))
                   (range (bp/nodes-count nodes)))
             (finally (bp/nodes-free nodes)))))))))
