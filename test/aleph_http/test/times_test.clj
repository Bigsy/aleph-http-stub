(ns aleph-http.test.times-test
  (:require [clojure.test :refer :all]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [aleph-http.stub :refer [with-http-stub]]))

(deftest test-times-verification
  (testing "passes when route is called expected number of times"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 2}}
        ;; Make two calls as required
        (d/chain (http/get "http://example.com")
                (fn [_] (http/get "http://example.com"))
                (fn [_] (deliver p :done)))
        @p)))

  (testing "fails when route is called less than expected times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 2}}
        @(http/get "http://example.com"))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 2 times but was called 1 times")))))

  (testing "fails when route is called more than expected times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 1}}
        @(d/chain (http/get "http://example.com")
                 (fn [_] (http/get "http://example.com"))))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 1 times but was called 2 times"))))))

(deftest test-times-edge-cases
  (testing "passes when route with :times 0 is never called"
    (with-http-stub
      {"http://example.com"
       {:get (fn [_] {:status 200 :body "ok"})
        :times 0}}))

  (testing "fails when route with :times 0 is called"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :times 0}}
        @(http/get "http://example.com"))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 0 times but was called 1 times"))))))
