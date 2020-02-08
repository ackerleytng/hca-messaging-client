(ns app.renderer.core
  (:require [reagent.core :as r]
            [clojure.string :as string]
            ["electron" :refer [remote]]))

(enable-console-print!)

(declare app-state)

;;----------------------------------------------
;; Helper functions
;;----------------------------------------------

(defn parse-recipient
  [line]
  (let [[name number] (rest (re-find #"(\w+)\s*(\d+)" line))]
    (if (and name number)
      {:name name :phone-number number}
      {:name line
       :phone-number (str "Error: Can't understand \"" line "\"")})))

(defn parse-recipients
  [text]
  (->> text
       string/split-lines
       (filter #(> (count (string/trim %)) 0))
       (map parse-recipient)))

(defn render-template
  [template name]
  (string/replace template #"\{\{ name \}\}" name))

(def fs (.require remote "fs"))
(def save-path "./saved-template.txt")
(defn save-template []
  (fs.writeFileSync save-path (:message-template @app-state))
  (swap! app-state assoc :show-saved-modal true))

(defn read-if-exists [path]
  (if (fs.existsSync path)
    (fs.readFileSync path "utf8")
    ""))

(defn restore-template []
  (swap! app-state assoc :message-template (read-if-exists save-path)))

;;----------------------------------------------
;; app-state
;;----------------------------------------------

(def app-state
  (r/atom {:recipients ""
           :message-template ""
           :show-confirm-modal false
           :show-saved-modal false}))

;;----------------------------------------------
;; Components
;;----------------------------------------------

(defn name-number-table
  [recipients]
  [:table.table.is-bordered.is-narrow
   [:tr [:th "Name"] [:th "Phone Number"]]
   (for [r recipients]
     ^{:key r} [:tr [:td (:name r)] [:td (:phone-number r)]])])

(defn sample-message-display
  [message-template recipients]
  [:pre
   (if (> (count recipients) 0)
     (render-template message-template (:name (first recipients)))
     "No recipients found")])

(defn confirm-modal [show message-template recipients]
  [:div.modal {:style {:display (if show :flex :none)}}
   [:div.modal-background {:on-click #(swap! app-state assoc :show-confirm-modal false)}]
   [:div.modal-card
    [:header.modal-card-head
     [:p.modal-card-title "Check before sending"]
     [:button.delete {:on-click #(swap! app-state assoc :show-confirm-modal false)}]]
    [:section.modal-card-body
     [:div.field
      [:label.label "Sample message for Alice"]
      [sample-message-display message-template recipients]]
     [:div.field
      [:label.label "The above will be sent to the following people"]
      [:div {:style {:padding-left "20px"}}
       (if (> (count recipients) 0)
         [name-number-table recipients]
         [:p "No recipients found"])]]]
    [:footer.modal-card-foot
     [:button.button
      {:on-click #(swap! app-state assoc :show-confirm-modal false)}
      "Cancel"]
     [:button.button.is-link
      {:on-click #(swap! app-state assoc :show-confirm-modal false)}
      "Send"]]]])

(defn saved-modal [show]
  [:div.modal {:style {:display (if show :flex :none)}}
   [:div.modal-background {:on-click #(swap! app-state assoc :show-saved-modal false)}]
   [:div.modal-content
    [:article.message
     [:div.message-header
      [:p "Saved message"]]
     [:div.message-body
      [:p "Message template was saved to " [:code save-path]]]]]])

(defn root-component []
  [:div.container
   [confirm-modal
    (:show-confirm-modal @app-state)
    (:message-template @app-state)
    (parse-recipients (:recipients @app-state))]
   [saved-modal (:show-saved-modal @app-state)]
   [:div.field
    [:label.label "Recipients"]
    [:div.control
     [:textarea.textarea {:placeholder "Copy and paste name and phone numbers here"
                          :on-change #(swap! app-state assoc :recipients (-> % .-target .-value))}]]]
   [:div.field
    [:label.label "Message template"]
    [:div.control
     [:textarea.textarea {:placeholder "Copy and paste message template here"
                          :on-change #(swap! app-state assoc :message-template
                                             (-> % .-target .-value))
                          :value (:message-template @app-state)}]]]
   [:div.buttons
    [:button.button
     {:on-click save-template}
     "Save template"]
    [:button.button
     {:on-click restore-template}
     "Restore template"]
    [:button.button.is-link
     {:on-click #(swap! app-state assoc :show-confirm-modal true)}
     "Send"]]])

(defn start! []
  (restore-template)
  (r/render
   [:section.section
    [root-component]]
   (js/document.getElementById "app-container")))
