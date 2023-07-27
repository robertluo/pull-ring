(ns robertluo.pull-ring
  "Lasagna-pull extension for ring")

;;----
;; Ring data schemas
;; see https://github.com/ring-clojure/ring/blob/master/SPEC

(def ring-request-schema
  "Data schema for Ring requests"
  [:map
   [:server-port :int]
   [:server-name :string]
   [:remote-addr :string]
   [:uri [:and :string [:fn #(.startsWith % "/")]]]
   [:query-string {:optional true} :string]
   [:scheme [:enum :http :https]]
   [:request-method :keyword]
   [:protocol :string]
   [:ssl-client-cert {:optional true} [:fn #(instance? java.security.cert.X509Certificate %)]]
   [:headers [:map-of :string :string]]
   [:body {:optional true} [:fn #(instance? java.io.InputStream %)]]])

^:rct/test
(comment
  (require '[malli.core :as m])
  (m/validate ring-request-schema {}) ;=> false
  (m/validate ring-request-schema {:server-port 8080 :server-name "localhost" :remote-addr "localhost"
                                   :uri "/hello" :scheme :https :request-method :get 
                                   :protocol "HTTP/1.1" :headers {}}) ;=> true
  )

(def ring-response-schema
  "data schema for Ring responses"
  [:map
   [:status :int]
   [:headers [:map-of :string :string]]
   [:body {:optional true} [:fn #(instance? java.io.InputStream %)]]])
