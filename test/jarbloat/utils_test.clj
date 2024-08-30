(ns jarbloat.utils-test
  (:require [clojure.test :refer :all]
            [jarbloat.utils :as u]))

(deftest utils-test
  (testing "path-drop-last"
    (is (= (u/path-drop-last "foo/bar/baz") "foo/bar"))
    (is (= (u/path-drop-last "/") ""))
    (is (= (u/path-drop-last "foo/bar/baz" ".") "foo.bar"))
    (is (thrown? AssertionError (u/path-drop-last "invalid-path")))
    (is (thrown? AssertionError (u/path-drop-last nil)))
    ))
