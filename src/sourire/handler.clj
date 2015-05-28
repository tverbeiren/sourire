(ns sourire.handler
  (:require [bidi.ring :refer [make-handler]]
            [taoensso.timbre :refer [info error]]
            [sourire.core :refer [init-indigo+renderer render-to-buffer]]
            [sourire.victorinox :refer [url-decode]])
  (:use [ring.middleware params
         keyword-params
         nested-params])
  (:import (java.io ByteArrayInputStream))
  (:import com.ggasoftware.indigo.IndigoException))

(defn serve-index [_]
  {:status 200 
   :body "Usage: \n\n [base-url]/molecule/[url-encoded-smiles-string]?indigo-param-name=param-value"})

(defn serve-molecule-image [req]
  (info "serving request" req)
  (try
    (let [params (req :params)
          smi    (-> (params :smi) url-decode)
          opts   (-> params (dissoc :smi))
          i+r    (init-indigo+renderer opts)]
      (try
        (let [image (->> smi
                         (render-to-buffer i+r)
                         (ByteArrayInputStream.))]
          {:status  200
           :body    image
           :headers {"Content-Type" "image/png"}})
        (catch IndigoException e
          (error e)
          (let [backup-image (->> (url-decode "%5BBa%5D%5BC%5D%5BK%5D%5BU%5D%5BP%5D")
                                  (render-to-buffer i+r)
                                  (ByteArrayInputStream.))]
            {:status  200
             :body    backup-image
             :headers {"Content-Type" "image/png"}}))))
    (catch Exception e
      (error e)
      {:status 400
       :body   (str (.getMessage e))})))

(def molecule-regex #"[a-zA-Z0-9%\.\+\-\_\*\(\)]+")

(def all-routes ["/" {""           serve-index
                      "index.html" serve-index
                      ["molecule/" [molecule-regex :smi]] serve-molecule-image
                      ["molecule/" [molecule-regex :smi] "/image.png"] serve-molecule-image}])

(def api-handler (-> all-routes
                     make-handler
                     wrap-keyword-params
                     wrap-nested-params
                     wrap-params))
