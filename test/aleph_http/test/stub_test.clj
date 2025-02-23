(ns aleph-http.test.stub-test
  (:require [clojure.test :refer :all]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [aleph-http.stub :refer [with-http-stub with-http-stub-in-isolation]]))

(deftest test-simple-get
  (testing "Basic GET request with string URL"
    (let [response @(with-http-stub 
                     {"http://example.com" 
                      {:get (fn [_] {:status 200
                                    :headers {"Content-Type" "text/plain"}
                                    :body "Hello World"})}}
                     (http/get "http://example.com"))]
      (is (= 200 (:status response)))
      (is (= "text/plain" (get-in response [:headers "Content-Type"])))
      (is (= "Hello World" (bs/to-string (:body response)))))))

(deftest test-pattern-matching
  (testing "Pattern matching for URLs"
    (let [response @(with-http-stub 
                     {#"http://example.com/\d+" 
                      {:get (fn [_] {:status 200
                                    :body "Numbered resource"})}}
                     (http/get "http://example.com/123"))]
      (is (= 200 (:status response)))
      (is (= "Numbered resource" (bs/to-string (:body response)))))))

(deftest test-method-specific-response
  (testing "Different responses for different HTTP methods"
    (let [response1 @(with-http-stub 
                      {"http://example.com" 
                       {:post (fn [_] {:status 201 :body "Created"})
                        :get (fn [_] {:status 200 :body "OK"})}}
                      (http/post "http://example.com"))
          response2 @(with-http-stub 
                      {"http://example.com" 
                       {:post (fn [_] {:status 201 :body "Created"})
                        :get (fn [_] {:status 200 :body "OK"})}}
                      (http/get "http://example.com"))]
      (is (= 201 (:status response1)))
      (is (= "Created" (bs/to-string (:body response1))))
      (is (= 200 (:status response2)))
      (is (= "OK" (bs/to-string (:body response2)))))))

(deftest test-query-params
  (testing "Query params matching"
    (let [response @(with-http-stub 
                     {"http://example.com/api" 
                      {:get (fn [req] 
                             (if (= (get-in req [:query-params :q]) "test")
                               {:status 200 :body "Found"}
                               {:status 404 :body "Not Found"}))}}
                     (http/get "http://example.com/api?q=test"))]
      (is (= 200 (:status response)))
      (is (= "Found" (bs/to-string (:body response)))))))

(deftest test-any-method
  (testing "Any method matching"
    (let [response1 @(with-http-stub 
                      {"http://example.com" 
                       {:any (fn [_] {:status 200 :body "Any"})}}
                      (http/get "http://example.com"))
          response2 @(with-http-stub 
                      {"http://example.com" 
                       {:any (fn [_] {:status 200 :body "Any"})}}
                      (http/post "http://example.com"))]
      (is (= "Any" (bs/to-string (:body response1))))
      (is (= "Any" (bs/to-string (:body response2)))))))

(deftest test-http-stub-in-isolation
  (testing "throws exception for unmatched routes in isolation mode"
    (is (thrown? clojure.lang.ExceptionInfo
          @(with-http-stub-in-isolation
             {"http://example.com/matched" 
              {:get (fn [_] {:status 200 :body "OK"})}}
             (http/get "http://example.com/unmatched")))))
  
  (testing "matches routes correctly in isolation mode"
    (let [response @(with-http-stub-in-isolation
                     {"http://example.com/matched"
                      {:get (fn [_] {:status 200 :body "OK"})}}
                     (http/get "http://example.com/matched"))]
      (is (= 200 (:status response)))
      (is (= "OK" (bs/to-string (:body response)))))))
