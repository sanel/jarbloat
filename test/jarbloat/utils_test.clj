(ns jarbloat.utils-test
  (:require [clojure.test :refer :all]
            [jarbloat.utils :as u]))

(deftest utils-test
  (testing "path-drop-last"
    (is (= (u/path-drop-last "foo/bar/baz") "foo/bar"))
    (is (= (u/path-drop-last "/") ""))
    (is (= (u/path-drop-last "foo/bar/baz" ".") "foo.bar"))
    (is (= (u/path-drop-last "some-expr-that$will-not$be-chaged") "some-expr-that$will-not$be-chaged"))
    (is (= (u/path-drop-last nil) nil))
    )

  (testing "pp-bytes"
    (is (= (u/pp-bytes 1) "1.00B"))
    (is (= (u/pp-bytes 123123123) "117.42MB"))
    (is (= (u/pp-bytes 1111111111111111) "1010.55TB"))
    (is (= (u/pp-bytes 1111111111111111111111111) "1111111111111111111111111B")
        "Overflow. Write it just as bytes")
    ))
