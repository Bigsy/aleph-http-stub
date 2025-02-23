# aleph-http-stub 
[![MIT License](https://img.shields.io/badge/license-MIT-brightgreen.svg?style=flat)](https://www.tldrlegal.com/l/mit) 
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.bigsy/aleph-http-stub.svg)](https://clojars.org/org.clojars.bigsy/aleph-http-stub)

This is a library for stubbing out HTTP requests in Clojure specifically for the Aleph HTTP client. It provides both global and localized stubbing options for different testing scenarios. Stubbing can be isolated to specific test blocks to prevent unintended side effects.

## Usage

```clojure
(ns myapp.test.core
   (:require [aleph.http :as http])
   (:use aleph-http.stub))
```

The public interface consists of macros:

* `with-http-stub` - lets you override HTTP requests that match keys in the provided map
* `with-http-stub-in-isolation` - does the same but throws if a request does not match any key

### Examples

```clojure
;; Basic usage with deferred/promise handling
(let [p (promise)]
  (with-http-stub
    {"http://example.com"
     {:get (fn [_] {:status 200
                   :headers {"Content-Type" "text/plain"}
                   :body "Hello World"})}}
    (d/chain (http/get "http://example.com")
             (fn [{:keys [status headers body]}]
               ;; Process response
               (deliver p :done))))
  @p)

;; Route matching examples:
(with-http-stub
  {;; Exact string match:
   "https://api.github.com/users/octocat"
   (fn [request] {:status 200 :headers {} :body "{\"name\": \"The Octocat\"}"})

   ;; Exact string match with query params:
   "https://api.spotify.com/v1/search?q=beethoven&type=track"
   (fn [request] {:status 200 :headers {} :body "{\"tracks\": [...]}"})

   ;; Regexp match:
   #"https://([a-z]+).stripe.com/v1/customers"
   (fn [req] {:status 200 :headers {} :body "{\"customer\": \"cus_123\"}"})

   ;; Match based on HTTP method with query params:
   "http://example.com/api"
   {:get (fn [req]
           (if (= (get-in req [:query-params :q]) "test")
             {:status 200 :body "Found"}
             {:status 404 :body "Not Found"}))}

   ;; Match based on HTTP method:
   "https://api.slack.com/api/chat.postMessage"
   {:post (fn [req] {:status 200 :headers {} :body "{\"ok\": true}"})}

   ;; Match multiple HTTP methods:
   "https://api.dropbox.com/2/files"
   {:get    (fn [req] {:status 200 :headers {} :body "{\"entries\": [...]}"})
    :delete (fn [req] {:status 401 :headers {} :body "{\"error\": \"Unauthorized\"}"})
    :any    (fn [req] {:status 200 :headers {} :body "{\"status\": \"success\"}"})}

   ;; Match using query params as a map
   {:address "https://api.openai.com/v1/chat/completions" :query-params {:model "gpt-4"}}
   (fn [req] {:status 200 :headers {} :body "{\"choices\": [...]}"})

   ;; If not given, the stub response status will be 200 and the body will be "".
   "https://api.twilio.com/2010-04-01/Messages"
   (constantly {})}

 ;; Your tests with requests here
 )
```

### Isolation Mode

The `with-http-stub-in-isolation` macro works the same way as `with-http-stub` but will throw an exception if any request is made that doesn't match a stubbed route:

```clojure
;; This will throw an exception since the route is not matched
(with-http-stub-in-isolation
  {"http://example.com/matched"
   {:get (fn [_] {:status 200 :body "OK"})}}
  @(http/get "http://example.com/not-matched"))

;; This will work as expected
(with-http-stub-in-isolation
  {"http://example.com/matched"
   {:get (fn [_] {:status 200 :body "OK"})}}
  @(http/get "http://example.com/matched"))
```

### Working with Multiple Requests

You can use `manifold.deferred/zip` to handle multiple requests together:

```clojure
(with-http-stub
  {"http://example.com"
   {:post (fn [_] {:status 201 :body "Created"})
    :get (fn [_] {:status 200 :body "OK"})}}
  @(d/zip
    (http/post "http://example.com")
    (http/get "http://example.com")))
```

### Call Count Validation

You can specify and validate the number of times a route should be called using the `:times` option:

```clojure
(with-http-stub
  {"https://api.example.com/data"
   {:get (fn [_] {:status 200 :body "ok"})
    :times 2}}
  
  ;; This will pass - route is called exactly twice as expected
  @(http/get "https://api.example.com/data")
  @(http/get "https://api.example.com/data"))

;; Multiple methods with different counts
(with-http-stub
  {"https://api.example.com/data"
   {:get (fn [_] {:status 200 :body "ok"})
    :post (fn [_] {:status 201 :body "created"})
    :times {:get 2 :post 1}}}
  
  ;; This will pass - GET called twice, POST called once
  @(http/get "https://api.example.com/data")
  @(http/get "https://api.example.com/data")
  @(http/post "https://api.example.com/data"))
```

### URL Matching Details

The library provides the following URL matching capabilities:

1. Default ports:
   ```clojure
   ;; These are equivalent:
   "http://example.com:80/api"
   "http://example.com/api"
   ```

2. Trailing slashes:
   ```clojure
   ;; These are equivalent:
   "http://example.com/api/"
   "http://example.com/api"
   ```

3. Default schemes:
   ```clojure
   ;; These are equivalent:
   "http://example.com"
   "example.com"
   ```

4. Query parameter order independence:
   ```clojure
   ;; These are equivalent:
   "http://example.com/api?a=1&b=2"
   "http://example.com/api?b=2&a=1"
   ```

## License

Copyright © 2025 Your Name

Distributed under the MIT License.
