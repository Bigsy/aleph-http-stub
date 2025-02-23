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

(deftest test-method-specific-times
  (testing "passes when methods are called their expected number of times"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :post (fn [_] {:status 201 :body "created"})
          :times {:get 1 :post 2}}}
        (d/chain (http/get "http://example.com")
                (fn [_] (http/post "http://example.com" {:body "first"}))
                (fn [_] (http/post "http://example.com" {:body "second"}))
                (fn [_] (deliver p :done)))
        @p)))

  (testing "passes when methods with query params are called their expected number of times"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com"
         {:get (fn [req] 
                (case (get-in req [:query-params :type])
                  "users" {:status 200 :body "user list"}
                  "posts" {:status 200 :body "post list"}
                  {:status 400 :body "invalid type"}))
          :post (fn [req]
                 (if (= (get-in req [:query-params :action]) "create")
                   {:status 201 :body "created"}
                   {:status 400 :body "invalid action"}))
          :times {:get 2 :post 1}}}
        (d/chain (http/get "http://example.com" {:query-params {:type "users"}})
                (fn [_] (http/get "http://example.com" {:query-params {:type "posts"}}))
                (fn [_] (http/post "http://example.com" {:query-params {:action "create"}
                                                         :body "data"}))
                (fn [_] (deliver p :done)))
        @p)))

  (testing "returns error response for invalid query params"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com"
         {:get (fn [req] 
                (case (get-in req [:query-params :type])
                  "users" {:status 200 :body "user list"}
                  "posts" {:status 200 :body "post list"}
                  {:status 400 :body "invalid type"}))
          :post (fn [req]
                 (if (= (get-in req [:query-params :action]) "create")
                   {:status 201 :body "created"}
                   {:status 400 :body "invalid action"}))
          :times {:get 1 :post 1}}}
        (d/chain (http/get "http://example.com" {:query-params {:type "invalid"}})
                (fn [response]
                  (is (= 400 (:status response)))
                  (is (= "invalid type" (:body response)))
                  (http/post "http://example.com" {:query-params {:action "invalid"}
                                                   :body "data"}))
                (fn [response]
                  (is (= 400 (:status response)))
                  (is (= "invalid action" (:body response)))
                  (deliver p :done)))
        @p)))

  (testing "fails when get method is called too many times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :post (fn [_] {:status 201 :body "created"})
          :times {:get 1 :post 2}}}
        @(d/chain (http/get "http://example.com")
                 (fn [_] (http/get "http://example.com"))))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:get' to be called 1 times but was called 2 times")))))

  (testing "fails when post method is called too few times"
    (try
      (with-http-stub
        {"http://example.com"
         {:get (fn [_] {:status 200 :body "ok"})
          :post (fn [_] {:status 201 :body "created"})
          :times {:get 1 :post 2}}}
        @(d/chain (http/get "http://example.com")
                 (fn [_] (http/post "http://example.com" {:body "first"}))))
      (is false "Should have thrown an exception")
      (catch Exception e
        (is (= (.getMessage e)
               "Expected route 'http://example.com:post' to be called 2 times but was called 1 times"))))))
