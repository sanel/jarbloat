(ns jarbloat.utils-test
  (:require [clojure.test :refer :all]
            [jarbloat.utils :as u]))

(deftest utils-test
  (testing "path-cut"
    (is (= (u/path-cut "foo/bar/baz") "foo/bar"))
    (is (= (u/path-cut "") ""))
    (is (= (u/path-cut "foo/bar/baz" ".") "foo.bar"))
    (is (= (u/path-cut "foo.bar.baz$some_other" #"\." ".") "foo.bar"))
    (is (= (u/path-cut "some-expr-that$will-not$be-chaged") "some-expr-that$will-not$be-chaged"))
    (is (= (u/path-cut nil) nil))
    )

  (testing "pp-bytes"
    (is (= (u/pp-bytes 1) "1.00B"))
    (is (= (u/pp-bytes 123123123) "117.42MB"))
    (is (= (u/pp-bytes 1111111111111111) "1010.55TB"))
    (is (= (u/pp-bytes 1111111111111111111111111) "1111111111111111111111111B")
        "Overflow. Write it just as bytes")
    ))
