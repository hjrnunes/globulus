(ns globulus.standsim.consumables
  (:require [clojure.java.io :as io]
            [charred.api :as csv]
            [clojure.string :as str]))

(defn generate [{:keys [file]}]
  (let [pad-row (fn [row]
                  (let [padding (- 8 (count row))]
                    (concat row (repeat padding ""))))

        csvdata [["Ncons" 24]
                 ["Description" "eur_tree" "eur_kg" "eur_l" "eur_km" "eur_year" "eur_animal" "eur_h"]
                 ["Atlantic pine Seedlings" 0.18, 0, 0, 0, 0, 0, 0]
                 ["Eucalypt Seedlings" 0.2, 0, 0, 0, 0, 0, 0]
                 ["Cork oak Seedlings" 0.35, 0, 0, 0, 0, 0, 0]
                 ["Atlantic pine Seeds" 0, 22.5, 0, 0, 0, 0, 0]
                 ["Cork oak Seeds" 0, 3.3, 0, 0, 0, 0, 0]
                 ["Fertilizer for manual application (slow release)" 0, 1.3, 0, 0, 0, 0, 0]
                 ["Fertilizer for mechanical application" 0, 0.25, 0, 0, 0, 0, 0]
                 ["Fertilizer for mechanical application (subsoil)" 0, 0.3, 0, 0, 0, 0, 0]
                 ["Plant Protectors" 0.27, 0, 0, 0, 0, 0, 0]
                 ["Pesticides" 9999, 0, 0, 0, 0, 0, 0]
                 ["Diesel" 0, 0, 1, 0, 0, 0, 0]
                 ["Petrol" 0, 0, 1.2, 0, 0, 0, 0]
                 ["Maintenace annual costs" 0, 0, 0, 0, 5, 0, 0]
                 ["Fencing" 0, 0, 0, 22, 0, 0, 0]
                 ["Game additional costs (licences)" 0, 0, 0, 0, 5, 0, 0]
                 ["Game guard" 0, 0, 0, 0, 14000, 0, 0]
                 ["Cost of red deer male" 0, 0, 0, 0, 0, 700, 0]
                 ["Cost of red deer female" 0, 0, 0, 0, 0, 200, 0]
                 ["Game trophy" 0, 0, 0, 0, 0, 3500, 0]
                 ["Game meat" 0, 1, 0, 0, 0, 0, 0]
                 ["Specialized male labour cost" 0, 0, 0, 0, 0, 0, 11.23]
                 ["Non-specialized male labour cost" 0, 0, 0, 0, 0, 0, 6.73]
                 ["Specialized female labour cost" 0, 0, 0, 0, 0, 0, 8.43]
                 ["Non-specialized female labour cost" 0, 0, 0, 0, 0, 0, 5.05]
                 ["End of file"]]
        csvdata (->> csvdata
                     (mapv pad-row))]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf
                       :quote \'
                       :quote? (fn [s] (and (not (str/blank? s))
                                            (not (some? (parse-double s)))))))
      csvdata)))

