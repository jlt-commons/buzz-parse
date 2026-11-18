(ns buzz-parse.html-test
  (:require [clojure.test :refer [deftest testing is]]
            [buzz-parse.html :refer [parse parse-string parse-fragment select]]))

(defn- stream [s]
  (java.io.ByteArrayInputStream. (.getBytes s)))

(def ^:private empty-head {:tag :head :attrs nil :content nil})

;; --- crouton's suite, unchanged -----------------------------------------------

(deftest test-parse
  (testing "Elements"
    (is (= (parse (stream "<html><head></head><body></body></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content nil}]})))
  (testing "Attributes"
    (is (= (parse (stream "<html><body><div class=\"foo\"></div></body></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil
                       :content [{:tag :div
                                  :attrs {:class "foo"}
                                  :content nil}]}]})))
  (testing "Text"
    (is (= (parse (stream "<html><body>Hello World</body></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil
                       :content ["Hello World"]}]})))
  (testing "Scripts"
    (is (= (parse (stream "<html><head><script>foo();</script></head></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil
                       :content [{:tag :script
                                  :attrs nil
                                  :content ["foo();"]}]}
                      {:tag :body :attrs nil :content nil}]})))
  (testing "Comments"
    (is (= (parse (stream "<html><body>Foo<!--bar-->Baz</html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content ["FooBaz"]}]})))
  (testing "Doctypes"
    (is (= (parse (stream "<!DOCTYPE html><html></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content nil}]})))
  (testing "XML declarations"
    (is (= (parse (stream "<?xml version=\"1.0\" encoding=\"UTF-8\"><html></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content nil}]})))
  (testing "Character entities"
    (is (= (parse (stream "<html><body>x &amp; y</body></html>"))
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content ["x & y"]}]}))))

(deftest test-parse-string
  (testing "Elements"
    (is (= (parse-string "<html><head></head><body></body></html>")
           {:tag :html :attrs nil
            :content [{:tag :head :attrs nil :content nil}
                      {:tag :body :attrs nil :content nil}]}))))

;; --- jsoup behaviour crouton relies on ------------------------------------------

(defn- body [tree] (-> tree :content second))

(deftest test-crouton-semantics
  (testing "missing html/head/body are synthesized"
    (is (= (parse-string "<p>hi")
           {:tag :html :attrs nil
            :content [empty-head
                      {:tag :body :attrs nil
                       :content [{:tag :p :attrs nil :content ["hi"]}]}]})))
  (testing "text whitespace collapses to single spaces, as jsoup's TextNode.text()"
    (is (= ["a b c"] (:content (body (parse-string "<body>a \n\t b   c</body>"))))))
  (testing "script and style data keep their whitespace"
    (is (= ["a  =\n 1;"]
           (-> (parse-string "<script>a  =\n 1;</script>") :content first :content first :content))))
  (testing "bare attributes read as empty strings, names lowercase"
    (is (= {:disabled "" :type "checkbox"}
           (-> (parse-string "<input DISABLED type=checkbox>") body :content first :attrs))))
  (testing "utf-8 round-trips"
    (is (= ["héllo — 日本 😀"] (:content (body (parse-string "<body>héllo &mdash; 日本 😀</body>")))))))

;; --- lexbor extras -----------------------------------------------------------------

(def ^:private page
  "<html><body>
     <ul id=\"nav\"><li class=\"a\">One</li><li class=\"b\">Two <b>2</b></li></ul>
     <p class=\"a\">Para</p>
   </body></html>")

(deftest test-select
  (testing "matches come back as trees in document order"
    (is (= [{:tag :li :attrs {:class "a"} :content ["One"]}
            {:tag :p :attrs {:class "a"} :content ["Para"]}]
           (select page ".a"))))
  (testing "selector lists don't duplicate a node"
    (is (= 2 (count (select page "li, #nav li, li.b")))))
  (testing "combinators and pseudo-classes"
    (is (= [{:tag :b :attrs nil :content ["2"]}] (select page "ul > li:nth-child(2) b")))
    (is (= [:p] (map :tag (select page "li ~ p, body > :not(ul)")))))
  (testing "no matches"
    (is (= [] (select page "table"))))
  (testing ":as :html, :inner-html and :text"
    (is (= ["<li class=\"b\">Two <b>2</b></li>"] (select page "li.b" {:as :html})))
    (is (= ["Two <b>2</b>"] (select page "li.b" {:as :inner-html})))
    (is (= ["OneTwo 2"] (select page "#nav" {:as :text}))))
  (testing "a bad selector throws"
    (is (thrown? clojure.lang.ExceptionInfo (select page "li[")))
    (is (thrown? clojure.lang.ExceptionInfo (select page "li" {:as :bogus})))))

(deftest test-parse-fragment
  (testing "top-level nodes, no synthesized html/body"
    (is (= [{:tag :table :attrs nil
             :content [{:tag :tbody :attrs nil
                        :content [{:tag :tr :attrs nil
                                   :content [{:tag :td :attrs nil :content ["x"]}]}]}]}]
           (parse-fragment "<table><tr><td>x</td></tr></table>")))
    (is (= ["x"] (parse-fragment "<td>x</td>")) "body context drops a stray <td>, keeps its text")
    (is (= ["Hi " {:tag :b :attrs nil :content ["there"]} "!"]
           (parse-fragment "Hi <b>there</b><!-- c -->!"))))
  (testing "empty input"
    (is (= [] (parse-fragment "")))))

(deftest test-template
  (testing "template content is walked like children"
    (is (= [{:tag :template :attrs nil :content [{:tag :p :attrs nil :content ["t"]}]}]
           (parse-fragment "<template><p>t</p></template>")))))
