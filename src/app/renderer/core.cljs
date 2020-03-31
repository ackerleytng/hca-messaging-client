(ns app.renderer.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as string]
            ["electron" :refer [remote]]
            ["axios" :as axios]))

(enable-console-print!)

;; Uncomment this when using hot reload during dev
;; (def global (clj->js {:location {:search "placeholder"}}))

(declare app-state)
(def hca-client-token (subs global.location.search 1))

;;----------------------------------------------
;; Helper functions
;;----------------------------------------------

(defn render-template
  [template {:keys [name patient-name] :or {name "" patient-name ""}}]
  (-> template
      (string/replace #"\{\{ Name \}\}" name)
      (string/replace #"\{\{ Patient Name \}\}" patient-name)))

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

(defn- retain [[key value] [_ recipient]]
  (string/includes? (string/lower-case (str (or (get recipient key) ""))) (string/lower-case (or value ""))))

(defn filter-recipients [recipients recipients-filters]
  (let [non-blank-filters (filter #(> (count (second %)) 0) recipients-filters)]
    (if (seq non-blank-filters)
      (filter (apply some-fn (map #(partial retain %) non-blank-filters)) (seq recipients))
      recipients)))

(defn select-all-recipients []
  (let [{:keys [all-selected recipients recipients-filters]} @app-state
        visible-recipients (filter-recipients recipients recipients-filters)
        new-recipients (into {} (for [[k v] (:recipients @app-state)]
                                  [k (assoc v :selected (not all-selected))]))]
    (doseq [[k _] visible-recipients]
      (swap! app-state assoc-in [:recipients k :selected] (not all-selected)))
    (swap! app-state assoc :all-selected (not all-selected))))

(defn toggle-recipient [chat-id]
  (swap! app-state update-in [:recipients chat-id :selected] not))

(defn- to-keyword [s]
  ({"chatId" :chat-id
    "patientName" :patient-name
    "hahNum" :hah-num
    "name" :name} s))

(defn- clean-recipient [recipient]
  (into {} (for [[k v] recipient] [(to-keyword k) v])))

(defn- clean-recipients [recipients]
  (let [rs (map clean-recipient recipients)]
    (into {} (map (fn [r] [(:chat-id r) r]) rs))))

(defn retrieve-recipients []
  (-> (axios/get (str "https://prod.hcabot.workers.dev/listUsers/" hca-client-token "/"))
      (.then #(clean-recipients (get (js->clj %) "data")))
      (.then #(swap! app-state assoc :recipients %))))

(defn send-message-close-modal []
  (let [{:keys [recipients recipients-filters message-template]} @app-state
        visible-and-selected (filter-recipients recipients recipients-filters)
        ids (->> visible-and-selected
                 vals
                 (filter :selected)
                 (map :chat-id))
        message message-template
        data {:message message :chatIds ids}]
    (-> (axios/post
         (str "https://prod.hcabot.workers.dev/sendMessage/" hca-client-token "/")
         (clj->js data))
        (.then #(swap! app-state assoc :show-confirm-modal false)))))

(defn update-filter [filter-key v]
  (swap! app-state assoc-in [:recipients-filters filter-key] v))

;;----------------------------------------------
;; app-state
;;----------------------------------------------

(comment
  "Shape of recipients"
  {12321 {:name "Orange" :patient-name "Apple" :chat-id 12321 :hah-num 34125 :selected false}
   12323 {:name "Pear" :patient-name "Pineapple" :chat-id 12323 :hah-num 53479 :selected false}}
  )

(def app-state
  (r/atom {:all-selected false
           :recipients-filters {:name "" :patient-name "" :hah-num ""}
           :recipients {}
           :message-template ""
           :show-confirm-modal false
           :show-saved-modal false}))

;;----------------------------------------------
;; Components
;;----------------------------------------------

(defn confirm-name-number-table
  [recipients]
  [:table.table.is-bordered.is-narrow
   [:tbody
    [:tr [:th "Name"] [:th "Patient Name"] [:th "HAH Number"]]
    (for [[k {:keys [name patient-name hah-num]}] recipients]
      ^{:key k} [:tr [:td name] [:td patient-name] [:td hah-num]])]])

(defn sample-message-display
  [message-template recipient]
  [:pre
   (if (and name message-template)
     (render-template message-template recipient)
     "No recipients selected")])

(defn confirm-modal [show message-template recipients]
  (let [recipient (second (first (seq recipients)))]
    [:div.modal {:style {:display (if show :flex :none)}}
     [:div.modal-background {:on-click #(swap! app-state assoc :show-confirm-modal false)}]
     [:div.modal-card
      [:header.modal-card-head
       [:p.modal-card-title "Check before sending"]
       [:button.delete {:on-click #(swap! app-state assoc :show-confirm-modal false)}]]
      [:section.modal-card-body
       [:div.field
        [:label.label "Sample message"]
        [sample-message-display message-template recipient]]
       [:div.field
        [:label.label "The above will be sent to the following people"]
        [:div {:style {:padding-left "20px"}}
         (if (> (count recipients) 0)
           [confirm-name-number-table recipients]
           [:p "No recipients selected"])]]]
      [:footer.modal-card-foot
       [:button.button
        {:on-click #(swap! app-state assoc :show-confirm-modal false)}
        "Cancel"]
       [:button.button.is-link
        {:on-click send-message-close-modal}
        "Send"]]]]))

(defn saved-modal [show]
  [:div.modal {:style {:display (if show :flex :none)}}
   [:div.modal-background {:on-click #(swap! app-state assoc :show-saved-modal false)}]
   [:div.modal-content
    [:article.message
     [:div.message-header
      [:p "Saved message"]]
     [:div.message-body
      [:p "Message template was saved to " [:code save-path]]]]]])

(defn recipients-row [chat-id recipient]
  [:tr
   [:td [:input {:type "checkbox" :checked (boolean (:selected recipient)) :on-change #(toggle-recipient chat-id)}]]
   [:td (:name recipient)]
   [:td (:patient-name recipient)]
   [:td (:hah-num recipient)]])

(defn recipients-table [all-selected recipients {:keys [name patient-name hah-num] :as filters}]
  (if (> (count recipients) 0)
    [:table.table.is-narrow
     [:tbody
      [:tr
       [:th [:input {:type "checkbox" :checked all-selected :on-change select-all-recipients}]]
       [:th "Name"]
       [:th "Patient Name"]
       [:th "HAH Number"]]
      [:tr
       [:td]
       [:td [:input.input.is-small {:type "text" :value name :on-change #(update-filter :name (-> % .-target .-value))}]]
       [:td [:input.input.is-small {:type "text" :value patient-name :on-change #(update-filter :patient-name (-> % .-target .-value))}]]
       [:td [:input.input.is-small {:type "text" :value hah-num :on-change #(update-filter :hah-num (-> % .-target .-value))}]]]
      (for [[k v] (filter-recipients recipients filters)] ^{:key k} [recipients-row k v])]]
    [:div [:i "No recipients - retrieve them first?"]]))

(defn recipients-div [all-selected recipients filters]
  [:div
   [:div.field
    [:label.label "Recipients"]
    [:div [recipients-table all-selected recipients filters]]]
   [:div.buttons
    [:button.button
     {:on-click retrieve-recipients}
     "Retrieve"]]])

(defn root-component []
  [:div.container
   [confirm-modal
    (:show-confirm-modal @app-state)
    (:message-template @app-state)
    ;; Show only those visible AND selected
    (filter (comp :selected second) (filter-recipients (:recipients @app-state) (:recipients-filters @app-state)))]
   [saved-modal (:show-saved-modal @app-state)]
   [:div.columns
    [:div.column
     [recipients-div
      (:all-selected @app-state)
      (:recipients @app-state)
      (:recipients-filters @app-state)]]]
   [:div.columns
    [:div.column
     [:div.field
      [:label.label "Message template"]
      [:div.control
       [:textarea.textarea {:placeholder "Copy and paste message template here"
                            :on-change #(swap! app-state assoc :message-template
                                               (-> % .-target .-value))
                            :value (:message-template @app-state)}]]]]]
   [:div.columns
    [:div.column
     [:div.buttons
      [:button.button
       {:on-click save-template}
       "Save template"]
      [:button.button
       {:on-click restore-template}
       "Restore template"]
      [:button.button.is-link
       {:on-click #(swap! app-state assoc :show-confirm-modal true)}
       "Send"]]]]])

(defn start! []
  (restore-template)
  (rdom/render
   [:section.section
    [root-component]]
   (js/document.getElementById "app-container")))
