(ns buzz-parse.cli
  "Command-line entry point.

    buzz-parse [file] [options]
    jolt -m buzz-parse.cli [file] [options]

  Exits 2 on bad usage, a missing file or a selector lexbor can't parse."
  (:require [buzz-parse.html :as html]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [jolt.fs :as fs]))

(def usage
  "Usage: buzz-parse [file] [options]
   or: jolt -m buzz-parse.cli [file] [options]

Parses HTML from file, or stdin when file is - or missing, and prints the
tree crouton/clojure.xml style.

  -s, --select SEL  Print the nodes matching a CSS selector instead
  --as AS           With --select: tree (default), html, inner-html or text.
                    html and text print one match per line unless --format
                    is given
  --fragment        Parse the input as a fragment of a <body>
  --format FORMAT   edn (default) or json
  -h, --help        Show this")

(def ^:private value-options
  {"--select" {:key :select :parse identity}
   "-s"       {:key :select :parse identity}
   "--as"     {:key :as
               :parse #{"tree" "html" "inner-html" "text"}
               :error "--as takes tree, html, inner-html or text"}
   "--format" {:key :format
               :parse #{"edn" "json"}
               :error "--format takes edn or json"}})

(defn- parse-args
  "{:options ..} or {:error msg}."
  [args]
  (loop [[a & more :as args] args, opts {}]
    (cond
      (empty? args) {:options opts}

      (#{"-h" "--help"} a) {:options (assoc opts :help true)}

      (= "--fragment" a) (recur more (assoc opts :fragment true))

      (value-options a)
      (let [{:keys [key parse error]} (value-options a)
            v (first more)]
        (cond
          (nil? v) {:error (str a " takes a value")}
          (nil? (parse v)) {:error error}
          :else (recur (rest more) (assoc opts key (parse v)))))

      (and (str/starts-with? a "-") (not= "-" a)) {:error (str "unknown option " a)}

      (:file opts) {:error (str "only one input file, got " (:file opts) " and " a)}

      :else (recur more (assoc opts :file a)))))

(defn- check [{:keys [select as fragment] :as opts}]
  (cond
    (and as (not select)) {:error "--as needs --select"}
    (and fragment select) {:error "--select works on a whole document, not with --fragment"}
    :else {:options opts}))

(defn- render [value fmt]
  (if (= fmt "json")
    (str (json/write-str value) "\n")
    (str (pr-str value) "\n")))

(defn- output [{:keys [select as fragment format]} input]
  (cond
    fragment (render (html/parse-fragment input) format)

    select
    (let [as (keyword (or as "tree"))
          matches (html/select input select {:as as})]
      (if (or (= as :tree) format)
        (render matches format)
        (apply str (map #(str % "\n") matches))))

    :else (render (html/parse-string input) format)))

(defn run
  "Runs the CLI over args; returns {:exit n :out s :err s}. read-stdin is a
  no-arg fn returning the stdin text, so tests needn't touch the process's."
  [args {:keys [read-stdin]}]
  (let [{:keys [options error]} (let [r (parse-args args)]
                                  (if (:error r) r (check (:options r))))
        fail (fn [msg] {:exit 2 :out "" :err (str "buzz-parse: " msg "\n" "Try --help\n")})]
    (cond
      error (fail error)
      (:help options) {:exit 0 :out (str usage "\n") :err ""}
      :else
      (let [file (:file options)]
        (if (and file (not= "-" file) (not (fs/exists? file)))
          (fail (str "no such file " file))
          (try
            {:exit 0 :err ""
             :out (output options (if (and file (not= "-" file))
                                    (slurp file)
                                    (read-stdin)))}
            (catch clojure.lang.ExceptionInfo e
              (fail (ex-message e)))))))))

(defn -main [& args]
  (let [{:keys [exit out err]} (run args {:read-stdin #(slurp *in*)})]
    (print out)
    (flush)
    (when (seq err)
      (binding [*out* *err*] (print err) (flush)))
    (System/exit exit)))
