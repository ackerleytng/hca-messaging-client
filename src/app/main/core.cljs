(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow crashReporter Menu]]))

(def fs (js/require "fs"))
(def path (js/require "path"))

(def main-window (atom nil))

(defn init-browser []
  (reset!
   main-window
   (BrowserWindow.
    (clj->js {:width 800
              :height 600
              :webPreferences {:nodeIntegration true}})))

  ;; Path is relative to the compiled js file (main.js in our case)
  (if-let [token (or
                  ;; Dev mode
                  process.env.HCA_CLIENT_TOKEN
                  ;; Prod mode
                  (fs.readFileSync (path.join js/__dirname "../hcaClientToken")))]
    (.loadURL @main-window (str "file://" js/__dirname "/public/index.html?" token)))
  (.on @main-window "closed" #(reset! main-window nil)))

(defn main []
  ;; CrashReporter can just be omitted
  (.start crashReporter
          (clj->js {:companyName "MyAwesomeCompany"
                    :productName "MyAwesomeApp"
                    :submitURL "https://example.com/submit-url"
                    :autoSubmit false}))

  (.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                  (.quit app)))
  (.on app "ready" init-browser)

  ;; Disable menu
  ;; (.setApplicationMenu menu nil)
  )
