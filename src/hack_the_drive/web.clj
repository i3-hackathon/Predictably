(ns hack-the-drive.web
  (:import org.apache.commons.io.IOUtils)
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [ring.middleware [multipart-params :as mp]]
            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.util.response :as resp]
            [ring.middleware.params :refer [wrap-params]]
            [monger.util :refer [get-id]]
            [environ.core :refer [env]]
            [hack-the-drive.api.bmwcar :as car]
            [hack-the-drive.mojio.api :as mojio]
            [hack-the-drive.view.views :refer [index-view watch-view]]
            [hack-the-drive.storage :as storage :refer [store-media retrieve-media retrieve-data]]
            [hack-the-drive.capture :refer [capture-vehicle vehicle-details]]
            [hack-the-drive.view.grid :refer [grid-view]]
            [hack-the-drive.thermal :refer [thermal-intensity image-from-bytes]]))

(defn splash []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body (pr-str ["Hello" :from 'Heroku])})

(defn render [t]
  (apply str t))

(defn render-media-response [record]
    (-> (resp/response (new java.io.ByteArrayInputStream (:bytes record))) ; (java.io.ByteArrayInputStream. 
        (resp/content-type (:content-type record))
        (resp/header "Content-Length" (count (:bytes record)))))

; (count (retrieve-data "test"))
; (str (:_id (first (retrieve-data "media"))))

; (:bytes (retrieve-media "54b1fab703642f60fb7a441a"))
; (render-media-response (retrieve-media "54b1fab703642f60fb7a441a"))

(defn get-current-temperature [vehicle]
  (let [vehicle (first (mojio/all-vehicles))]
    {:temperature (or (:temperature vehicle) "70")}))

(defroutes app
  (GET  "/" [] 
    (resp/redirect "/grid"))
  (GET "/watch" [] 
    (render (watch-view)))
  (GET "/grid" []
    (render (grid-view (map #(assoc % :img (str "/media/" (:_id %))
                                      :vid (:vehicle %))
                            (retrieve-data "media"))
                       (car/all-vehicles))))
  (mp/wrap-multipart-params
     (POST "/capture" {params :params} 
        (let [content (get params :image)
              bytes (or (:bytes content)
                        (if (get content :tempfile)
                          (IOUtils/toByteArray (io/input-stream (get content :tempfile)))))
              data
                (dissoc
                  (merge
                   {:bytes bytes}
                   (get-current-temperature)
                   (vehicle-details (get params "vehicle")) 
                   (if bytes {:intensity (thermal-intensity (image-from-bytes bytes) 20)})
                   (assoc content
                          :vehicle (get params "vehicle")))
                  :tempfile :error)
              id (store-media data)
          ]
          ; (render (success))
          ; (resp/redirect  (clojure.string/replace "/media/:id" #":id" (str id)))))
            (resp/redirect "/grid")
          ; {:status 200 :headers {"Content-Type" "text/plain"} :body (pr-str data)})
        ;  {:status 200 :headers {"Content-Type" "text/plain"} :body (pr-str content)})
        ))
        {:store (byte-array-store)})
  (GET "/media/:id" [id]
    (render-media-response 
     (retrieve-media id)))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))
