(ns aleph-http.test.stub-test
  (:require [clojure.test :refer :all]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [aleph-http.stub :refer [with-http-stub with-http-stub-in-isolation]]))

(deftest simple-get-test
  (testing "Basic GET request stubbing with exact URL match"
    (let [response @(with-http-stub
                     {"http://example.com/test"
                      (fn [_] {:status 200
                              :headers {"content-type" "text/plain"}
                              :body "Hello, World!"})}
                     (http/get "http://example.com/test"))]
      (is (= 200 (:status response)))
      (is (= "Hello, World!" (bs/to-string (:body response))))))

  (testing "Basic GET request stubbing with regex pattern"
    (let [response @(with-http-stub
                     {#"http://example.com/.*"
                      (fn [_] {:status 201
                              :headers {"content-type" "text/plain"}
                              :body "Pattern matched!"})}
                     (http/get "http://example.com/anything"))]
      (is (= 201 (:status response)))
      (is (= "Pattern matched!" (bs/to-string (:body response)))))))

(deftest isolation-mode-test
  (testing "Throws exception for unmatched routes in isolation mode"
    (is (thrown? clojure.lang.ExceptionInfo
          @(with-http-stub-in-isolation
             {"http://example.com/matched" (fn [_] {:status 200 :body "OK"})}
             (http/get "http://example.com/unmatched"))))))
