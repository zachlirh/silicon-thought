# FitForm

An Android fitness app that combines an **on-device squat form coach** with the
everyday tools you need around a training session: **calorie** and **body fat**
calculators, an **exercise routine logger**, and a **statistics dashboard**.

Everything runs **fully on-device**. There are no accounts, no servers, and no
network calls.

---

## Features

### 1. Squat Coach (camera + on-device pose AI)
Point the back camera at yourself and the app tracks your body with ML Kit Pose
Detection.

- Counts reps automatically.
- Checks form each rep: **depth**, **knee valgus** (knees caving in),
  **forward lean**, and **uneven stance**.
- Speaks feedback with text-to-speech while you train.
- A **pose-validation gate** ignores frames where the body isn't fully and
  reliably visible, so it won't count phantom reps when the room is empty.
  Reps only start counting once you hold a steady standing stance.

### 2. Calorie Calculator
Estimates daily calorie needs from age, gender, height, weight and activity
level.

- Basal Metabolic Rate (BMR) and maintenance (TDEE).
- Daily targets for **mild / moderate / aggressive weight loss** (≈0.25, 0.5 and
  1 kg per week) plus the equivalent gain targets.
- **Metric or US customary** units.

### 3. Body Fat Calculator
Estimates body composition and reports **body fat %**, **body fat mass** and
**lean body mass** (plus BMI).

- **U.S. Navy method** — uses height and circumference measurements
  (neck / waist, and hip for women).
- **BMI method** — estimates body fat from BMI, age and gender.
- **Metric or US customary** units.

### 4. Exercise Routine Logger
Create and save custom routines, then log your workout as you go.

- Custom **exercise names**, adjustable **set counts**, and one or more
  **muscle groups** per exercise.
- Record **weight** and **reps** for every set.
- A **timer** starts when you tap *Start Workout* and stops on *Finish Workout*.
- The summary shows the **total workout duration** and the **total training
  volume** (the sum of `weight × reps` over every completed set).

### 5. Statistics Dashboard
Track attendance and progress over time.

- **Calendar** highlighting the days you trained.
- **Monthly report**: total workout days, cumulative time trained, and total
  training volume.
- **Radar (spider) chart** showing how training volume is distributed across six
  muscle groups — **back, chest, core, shoulders, arms, legs** — so you can spot
  areas getting relatively more or less work.

---

## Screens

| Screen | Purpose |
| --- | --- |
| Home | Hub linking the squat coach and the tools |
| Squat Coach | Live camera coaching and rep counting |
| Routines | List, create, edit and delete routines |
| Routine Editor | Build a routine (exercises, sets, muscle groups) |
| Workout Log | Run a session, log sets, timer + summary |
| Statistics | Calendar, monthly totals, radar chart |
| Calorie Calculator | Daily calorie targets |
| Body Fat Calculator | Body fat % / fat mass / lean mass |

---

## Tech stack

- **Kotlin** with Android Views + ViewBinding (Material 3)
- **minSdk 26**, **targetSdk 34**, compiled against **SDK 34**
- **CameraX 1.3.4** for the camera
- **ML Kit Pose Detection 18.0.0-beta5** (bundled model, on-device)
- No database dependency — workout data is persisted as **JSON** in app-private
  storage; the charts are **custom `Canvas` views**

---

## Architecture

The code is split so that the "thinking" is separate from the Android plumbing,
which keeps it testable and easy to extend (or extract).

```
core/   Pure Kotlin domain. No Android dependencies.
        - Models.kt        units, gender, activity levels, muscle groups,
                           routine / session / set models
        - Calculators.kt   calorie (BMR/TDEE) + body fat (Navy / BMI) math
        - WorkoutMath.kt   volume, durations, monthly stats, muscle volumes

data/   Storage.
        - WorkoutStore.kt  JSON persistence for routines and logged sessions
        - Prefs.kt         shared unit-system preference

ui/     Reusable custom views.
        - RadarChartView.kt     spider chart for muscle-group volume
        - MonthCalendarView.kt  month grid with workout-day highlights

*Activity.kt   Screens wiring the domain + storage to the UI.
```

### Project layout
```
app/src/main/java/com/siliconthought/fitform/
├─ MainActivity.kt              # home hub
├─ WorkoutActivity.kt           # squat coach screen
├─ NutritionActivity.kt         # calorie calculator
├─ BodyFatActivity.kt           # body fat calculator
├─ RoutinesActivity.kt          # routine list
├─ RoutineEditActivity.kt       # routine editor
├─ WorkoutLogActivity.kt        # active workout log
├─ StatsActivity.kt             # calendar + stats + radar
├─ analysis/                    # squat logic
│  ├─ SquatAnalyzer.kt          #   rep counting + form checks
│  └─ Angles.kt                 #   joint-angle helpers
├─ camera/
│  └─ PoseAnalyzer.kt           # CameraX + ML Kit pose detection
├─ core/                        # domain logic (pure Kotlin)
│  ├─ Models.kt
│  ├─ Calculators.kt
│  └─ WorkoutMath.kt
├─ data/                        # JSON storage + prefs
│  ├─ WorkoutStore.kt
│  └─ Prefs.kt
└─ ui/                          # custom views
   ├─ PoseOverlayView.kt        #   skeleton overlay
   ├─ RadarChartView.kt         #   spider chart
   └─ MonthCalendarView.kt      #   month grid
```

---

## Building

Requirements: **JDK 17**, **Android SDK 34**, Gradle 8.7 (wrapper included).

```bash
# debug build
./gradlew :app:assembleDebug
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device (Android 8.0 / API 26 or newer):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

---

## Permissions & privacy

- `CAMERA` — required by the squat coach. Pose detection runs **on-device**;
  camera frames are never uploaded or stored.
- Workout history, routines and the unit preference are stored **locally** in
  the app's private storage. Clearing the app's data removes everything.

---

## How the numbers are calculated

- **BMR** — Mifflin-St Jeor equation.
- **Maintenance (TDEE)** — BMR × activity factor (1.2 sedentary … 1.9 extra
  active).
- **Weight-change targets** — 250 kcal/day per ≈0.25 kg (0.5 lb) per week, so
  the 0.25 / 0.5 / 1 kg-per-week goals adjust maintenance by 250 / 500 / 1000
  kcal.
- **Body fat (U.S. Navy)** — circumference-based estimate using
  height, neck, waist (and hip for women).
- **Body fat (BMI method)** — Deurenberg estimate from BMI, age and gender.
- **Training volume** — `Σ (weight × reps)` over completed sets. An exercise
  assigned to several muscle groups credits its volume to each of them in the
  radar chart.

> These figures are **estimates** for general guidance and are not medical
> advice. Body-fat formulas in particular carry a few percent of error — track
> the **trend** rather than any single reading.

---

## Known limitations

- Pose-based form feedback depends on lighting, clothing and framing; good
  lighting and fitted clothing improve tracking.
- The form checks on the squat coach are rule-based (joint-angle thresholds),
  not a trained model — see "Possible next steps".
- Routine/statistics data is stored on the device only; there is no cloud sync.

---

## Possible next steps

- **Learned form judging** — record labelled reps and train a small model to
  score rep quality (good/bad and specific faults), running on-device.
- Automatic rep detection feeding directly into the routine logger.
- More exercises with dedicated form checks (push-ups, deadlifts, planks).
- Cloud sync and cross-device history.
- Unit-aware defaults and per-exercise history charts.
