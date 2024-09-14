(ns jarbloat.analyzer-test
  (:require [clojure.test :refer :all]
            [jarbloat.analyzer :as a]))

(deftest analyzer-test
  (testing "package-filter"
    (is (= (a/package-filter {:package "org.clojure"} [#"^org"] nil)
           {:package "org.clojure"}))

    (is (= (a/package-filter {:package "com.clojure"} [#"^org"] nil)
           nil))

    (is (= (a/package-filter {:package "org.clojure"} [#"^org"] [#"^org"])
           {:package "org.clojure"})
        "inclure-re has precedence")

    (is (= (a/package-filter {:package "org.clojure"} nil [#"^org"])
           nil))

    (is (= (a/package-filter {:package "org.clojure"} nil [#"^com"])
           {:package "org.clojure"}))

    (is (= (a/package-filter {:package "org.clojure"} [#".+\.clojure.*$" #"^com"] nil)
           {:package "org.clojure"}))

    ;; if inclue-re and exclude-re are not set, filtering is not applied
    (is (= (a/package-filter {:package "org.clojure"} nil nil)
           {:package "org.clojure"}))

    (is (= (a/package-filter {:package "org.clojure"} [] [])
           {:package "org.clojure"}))
  ))
