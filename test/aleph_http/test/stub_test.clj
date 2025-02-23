(ns aleph-http.test.stub-test
  (:require [clojure.test :refer :all]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [byte-streams :as bs]
            [aleph-http.stub :refer [with-http-stub with-http-stub-in-isolation]]))

(deftest test-simple-get
  (testing "Basic GET request with string URL"
    (let [p (promise)]
      (with-http-stub 
        {"http://example.com" 
         {:get (fn [_] {:status 200
                       :headers {"Content-Type" "text/plain"}
                       :body "Hello World"})}}
        (d/chain (http/get "http://example.com")
                (fn [{:keys [status headers body]}]
                  (is (= 200 status))
                  (is (= "text/plain" (get headers "Content-Type")))
                  (is (= "Hello World" (bs/to-string body)))
                  (deliver p :done))))
      @p)))

(deftest test-pattern-matching
  (testing "Pattern matching for URLs"
    (let [p (promise)]
      (with-http-stub 
        {#"http://example.com/\d+" 
         {:get (fn [_] {:status 200
                       :body "Numbered resource"})}}
        (d/chain (http/get "http://example.com/123")
                (fn [{:keys [status body]}]
                  (is (= 200 status))
                  (is (= "Numbered resource" (bs/to-string body)))
                  (deliver p :done))))
      @p)))

(deftest test-method-specific-response
  (testing "Different responses for different HTTP methods"
    (let [p (promise)]
      (with-http-stub 
        {"http://example.com" 
         {:post (fn [_] {:status 201 :body "Created"})
          :get (fn [_] {:status 200 :body "OK"})}}
        (d/chain
         (d/zip
          (http/post "http://example.com")
          (http/get "http://example.com"))
         (fn [[{post-status :status post-body :body}
               {get-status :status get-body :body}]]
           (is (= 201 post-status))
           (is (= "Created" (bs/to-string post-body)))
           (is (= 200 get-status))
           (is (= "OK" (bs/to-string get-body)))
           (deliver p :done))))
      @p)))

(deftest test-query-params
  (testing "Query params matching"
    (let [p (promise)]
      (with-http-stub 
        {"http://example.com/api" 
         {:get (fn [req] 
                 (if (= (get-in req [:query-params :q]) "test")
                   {:status 200 :body "Found"}
                   {:status 404 :body "Not Found"}))}}
        (d/chain (http/get "http://example.com/api?q=test")
                (fn [{:keys [status body]}]
                  (is (= 200 status))
                  (is (= "Found" (bs/to-string body)))
                  (deliver p :done))))
      @p)))

(deftest test-any-method
  (testing "Any method matching"
    (let [p (promise)]
      (with-http-stub 
        {"http://example.com" 
         {:any (fn [_] {:status 200 :body "Any"})}}
        (d/chain
         (d/zip
          (http/get "http://example.com")
          (http/post "http://example.com"))
         (fn [[{get-body :body} {post-body :body}]]
           (is (= "Any" (bs/to-string get-body)))
           (is (= "Any" (bs/to-string post-body)))
           (deliver p :done))))
      @p)))

(deftest test-http-methods
  (testing "Various HTTP methods"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com"
         {:put (fn [req] {:status 200 :body "Updated"})
          :delete (fn [req] {:status 204})
          :patch (fn [req] {:status 200 :body "Partially updated"})}}
        
        (d/chain
         (d/zip
          (http/put "http://example.com" {:body "update data"})
          (http/delete "http://example.com")
          (http/request {:request-method :patch
                        :url "http://example.com"
                        :body "patch data"}))
         (fn [[put-resp delete-resp patch-resp]]
           (is (= 200 (:status put-resp)))
           (is (= "Updated" (bs/to-string (:body put-resp))))
           (is (= 204 (:status delete-resp)))
           (is (nil? (:body delete-resp)))
           (is (= 200 (:status patch-resp)))
           (is (= "Partially updated" (bs/to-string (:body patch-resp))))
           (deliver p :done))))
      @p)))

(deftest test-http-stub-in-isolation
  (testing "throws exception for unmatched routes in isolation mode"
    (let [p (promise)]
      (try
        (with-http-stub-in-isolation
          {"http://example.com/matched" 
           {:get (fn [_] {:status 200 :body "OK"})}}
          (http/get "http://example.com/unmatched"))
        (catch clojure.lang.ExceptionInfo e
          (is (instance? clojure.lang.ExceptionInfo e))
          (is (= "No matching stub found and running in isolation mode"
                 (.getMessage e)))
          (deliver p :done)))
      @p))
  
  (testing "matches routes correctly in isolation mode"
    (let [p (promise)]
      (with-http-stub-in-isolation
        {"http://example.com/matched"
         {:get (fn [_] {:status 200 :body "OK"})}}
        (d/chain (http/get "http://example.com/matched")
                (fn [{:keys [status body]}]
                  (is (= 200 status))
                  (is (= "OK" (bs/to-string body)))
                  (deliver p :done))))
      @p)))

(deftest test-global-http-stub-in-isolation
  (testing "global stub in isolation mode throws exception for unmatched routes"
    (let [p (promise)]
      (try
        (with-http-stub-in-isolation
          {"http://example.com" 
           {:get (fn [_] {:status 200 :body "OK"})}}
          (http/get "http://other.com"))
        (catch clojure.lang.ExceptionInfo e
          (is (instance? clojure.lang.ExceptionInfo e))
          (is (= "No matching stub found and running in isolation mode"
                 (.getMessage e)))
          (deliver p :done)))
      @p))

  (testing "global stub in isolation mode matches routes and returns response"
    (let [p (promise)]
      (with-http-stub-in-isolation
        {"http://example.com" 
         {:get (fn [_] {:status 200 :body "success"})}}
        (d/chain (http/get "http://example.com")
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "success" (bs/to-string body)))
                   (deliver p :done))))
      @p))

  (testing "global stub in isolation mode preserves dynamic bindings across multiple calls"
    (let [p1 (promise)
          p2 (promise)]
      (with-http-stub-in-isolation
        {"http://example.com" 
         {:get (fn [_] {:status 200 :body "first"})}}
        (d/chain (http/get "http://example.com")
                 (fn [{:keys [body]}]
                   (is (= "first" (bs/to-string body)))
                   (deliver p1 :done)))
        (try
          (http/get "http://other.com")
          (catch clojure.lang.ExceptionInfo e
            (is (instance? clojure.lang.ExceptionInfo e))
            (is (= "No matching stub found and running in isolation mode"
                 (.getMessage e)))
            (deliver p2 :done))))
      [@p1 @p2])))

(deftest test-global-http-stub
  (testing "matches routes correctly with global stub"
    (let [p (promise)]
      (with-http-stub
        {"http://example.com/matched" 
         {:get (fn [_] {:status 200 :body "OK"})}}
        (d/chain (http/get "http://example.com/matched")
                 (fn [{:keys [status body]}]
                   (is (= 200 status))
                   (is (= "OK" (bs/to-string body)))
                   (deliver p :done))))
      @p))

  (testing "preserves global stub across multiple calls"
    (let [p1 (promise)
          p2 (promise)]
      (with-http-stub
        {"http://example.com" 
         {:get (fn [_] {:status 200 :body "First"})
          :post (fn [_] {:status 201 :body "Second"})}}
        (d/chain
         (d/zip
          (http/get "http://example.com")
          (http/post "http://example.com"))
         (fn [[{get-body :body} {post-body :body post-status :status}]]
           (is (= "First" (bs/to-string get-body)))
           (is (= 201 post-status))
           (is (= "Second" (bs/to-string post-body)))
           (deliver p1 :done)
           (deliver p2 :done))))
      [@p1 @p2])))

