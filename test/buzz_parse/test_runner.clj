(ns buzz-parse.test-runner
  "Entry point for `jolt -M:test`. Requires each test namespace and runs
  clojure.test over them; exits non-zero on any failure."
  (:require [clojure.test :as t]
            [buzz-parse.html-test]
            [buzz-parse.cli-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'buzz-parse.html-test 'buzz-parse.cli-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
