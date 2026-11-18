(ns buzz-parse.cli-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [buzz-parse.cli :as cli]))

(def ^:private page "<ul><li class=a>One</li><li>Two <b>2</b></li></ul>")

(defn- run [args & {:keys [in] :or {in page}}]
  (cli/run args {:read-stdin (constantly in)}))

(deftest test-parse
  (testing "the whole document as EDN by default, from stdin"
    (let [{:keys [exit out]} (run [])]
      (is (= 0 exit))
      (is (= :html (:tag (edn/read-string out))))))
  (testing "- also means stdin"
    (is (= (run []) (run ["-"]))))
  (testing "a file argument"
    (let [f (str (System/getProperty "java.io.tmpdir") "/buzz-parse-cli-test.html")]
      (spit f "<p>file</p>")
      (is (= ["file"] (-> (run [f]) :out edn/read-string :content second :content first :content)))))
  (testing "json"
    (is (= "html" (get (json/read-str (:out (run ["--format" "json"]))) "tag"))))
  (testing "--fragment"
    (is (= [{:tag :li :attrs {:class "a"} :content ["One"]}
            {:tag :li :attrs nil :content ["Two " {:tag :b :attrs nil :content ["2"]}]}]
           (-> (run ["--fragment"] :in "<li class=a>One</li><li>Two <b>2</b></li>") :out edn/read-string)))))

(deftest test-select
  (testing "matches as trees"
    (is (= [{:tag :li :attrs {:class "a"} :content ["One"]}]
           (edn/read-string (:out (run ["--select" "li.a"]))))))
  (testing "--as html and text print one match per line"
    (is (= "<li class=\"a\">One</li>\n<li>Two <b>2</b></li>\n"
           (:out (run ["-s" "li" "--as" "html"]))))
    (is (= "One\nTwo 2\n" (:out (run ["-s" "li" "--as" "text"])))))
  (testing "--as with --format keeps the strings in a collection"
    (is (= ["One" "Two 2"] (edn/read-string (:out (run ["-s" "li" "--as" "text" "--format" "edn"])))))))

(deftest test-usage
  (testing "help"
    (let [{:keys [exit out]} (run ["--help"])]
      (is (= 0 exit))
      (is (str/starts-with? out "Usage: buzz-parse"))))
  (testing "bad usage exits 2 with a message on err"
    (doseq [args [["--bogus"] ["--format" "xml"] ["--as" "html"] ["--select"]
                  ["-s" "li["] ["--fragment" "-s" "li"] ["a.html" "b.html"]
                  ["/no/such/file.html"]]]
      (let [{:keys [exit err]} (run args)]
        (is (= 2 exit) (pr-str args))
        (is (not (str/blank? err)) (pr-str args))))))
