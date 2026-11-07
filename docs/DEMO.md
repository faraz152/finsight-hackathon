# FinSight on-stage demo (3.5 minutes)

Two people: **Presenter** (holds the phone, talks) and **Operator** (laptop, runs `adb`, mirrors the screen). Phone is connected by **USB cable** (adb over USB keeps working in airplane mode; adb over Wi-Fi does not).

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
```

---

## Pre-demo checklist (do 15 minutes before)

- [ ] **Fresh install** of the latest `main` build: `cd finsight-app && ./gradlew :composeApp:installDebug`. Keep a copy of the APK on the laptop.
- [ ] **Gemini key** is in `local.properties` *before* building. Privacy tab must show `Model: gemini-...`, not "No cloud key configured".
- [ ] **Notification access granted**: Privacy tab shows "Notification access: ON" (tap Refresh status). Or:
      `$ADB shell cmd notification allow_listener com.finsight.app/com.finsight.android.NotificationCaptureService`
- [ ] **Demo import done**: hexagon → Import messages (or Home → Import demo SMS). **Once only.** Write the counts from the Home line "Imported: N auto-accepted by rules · N need review · N duplicates merged · N dropped · N scams" here: ______________________
- [ ] **One correction made**: Transactions → open any merchant → pick a different category chip. It now shows the `You` badge. Leave one yellow record uncorrected for the live step.
- [ ] **Test notification once** (command below) and confirm it appears. Then change the TID digits in the command for the real demo so it lands as a fresh row (same TID is merged as a duplicate).
- [ ] **One hybrid question works on Wi-Fi/hotspot** (Health chip shows the `Hybrid` badge). Then Privacy → Cloud log → Clear, so the log on stage shows only the live call.
- [ ] **Do not swipe FinSight away** from recents after this point. Storage is in memory: killing the app empties it.
- [ ] Battery above 60%, charger in the bag, Do Not Disturb ON (but keep the shell notification visible; test it), screen timeout 10 min, brightness up, font size default.
- [ ] Hotspot on a second phone, already paired with the demo phone as a saved network.
- [ ] **Backup recording** made after one clean run-through:
      ```bash
      $ADB shell screenrecord --time-limit 180 /sdcard/demo.mp4
      $ADB pull /sdcard/demo.mp4 ~/Desktop/finsight-demo.mp4
      ```
      (180 s is the screenrecord maximum per file; record the hybrid part as a second file if needed.)
- [ ] App open on **Home**, airplane mode **OFF** (turn it on live), Coach chat empty.

---

## Run-sheet

Total target: **3 min 25 s**. Times are cumulative.

### 1. Hook (0:00 to 0:15) · Home
- **Say:** "Your phone already gets a message for every payment. To track money today you either type it all in, or hand your bank data to a server. And nobody tells you whether you're OK. FinSight fixes all three, on the phone."

### 2. Privacy proof (0:15 to 0:30) · Privacy tab
- **Do:** Pull down quick settings → **airplane mode ON**. Tap **Privacy** tab. Point to "What FinSight reads".
- **Say:** "We're offline now. FinSight has no SMS-inbox permission. It only sees notifications from SMS and wallet apps you allow. Everything else is ignored."

### 3. Live capture (0:30 to 0:55) · Transactions
- **Do:** Tap **Transactions**. Nod to Operator, who runs:
  ```bash
  $ADB shell "cmd notification post -S bigtext -t 8558 fs1 'Rs 1,250.00 sent to KFC Gulberg via JazzCash. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: 033050058799.'"
  ```
  A new row appears at the top: **KFC Gulberg**, Food, green, `Rule`. Tap it → **Why?** sheet: confidence, checks, original text.
- **Say:** "A JazzCash payment just arrived. No typing, no network. Tap 'Why?' and it shows its working: amount found, direction, merchant, category rule, confidence. The original text never leaves the phone."

### 4. Home overview (0:55 to 1:15) · Home
- **Do:** Close sheet, tap **Home**. Scroll slowly: month spend, **Financial health** tile, **Data coverage** tile, Pulse card, scam card, "Imported:" line.
- **Say:** "Here are the 78 messages we replayed earlier: [read the counts]. Duplicates merged, OTPs dropped, and a phishing SMS blocked. Data coverage tells you honestly how much of your money we can see. We never call you healthy on partial data."

### 5. Review and learn (1:15 to 1:40) · Transactions
- **Do:** **Transactions** → tap the **Review (N)** chip. Open a yellow record → **Why?** → tap the correct category chip. Badge becomes `You`.
- **Say:** "Uncertain records come to you, not silently into your totals. One tap fixes it, and FinSight remembers that merchant for every past and future payment."

### 6. Quick add (1:40 to 1:50) · hexagon
- **Do:** Tap the centre **hexagon** → **Add transaction** → tap chip **chai 150 cash** → **Add**.
- **Say:** "Cash too: same pipeline, categorized instantly." (Point at the locked **Scan receipt** on the way out: "receipts with on-device Gemma are next.")

### 7. Coach, still offline (1:50 to 2:10) · Coach
- **Do:** Hexagon → **Ask Coach**. Tap chip **Where did my money go this week?** → answer with bars and the `On-device` badge. Then tap chip **Meri spending kahan gayi?** → Roman Urdu answer. Tap **What was shared? ›**.
- **Say:** "Coach works in English and Roman Urdu. Still in airplane mode, so this is On-device. What was shared? Nothing left this phone."

### 8. Coach goes hybrid (2:10 to 2:45) · Coach
- **Do:** **Airplane mode OFF.** Wait until Wi-Fi shows connected (the router needs a validated connection, ~5 s; keep talking). Tap chip **How healthy are my finances and what should I fix first?** → `Hybrid` badge, Health score from the phone plus Gemini's advice. Tap **What was shared? ›** → JSON payload.
- **Say:** "Back online. Nobody switched a mode. The score is computed on this phone; Gemini only explains it. And here is exactly what it got: savings rate, rounded amounts, category shares. No merchants, no names, no account numbers. If Gemini makes up a number about you, our verifier deletes that sentence."

### 9. What If? (2:45 to 3:00) · Coach
- **Do:** Tap chip **Can I afford a laptop for PKR 150,000?** (or hexagon → **Can I afford it?**, which pre-fills "Can I afford PKR " → type `150000` → Send).
- **Say:** "A real decision: it checks your surplus, latest balance and keeps a one-month emergency reserve, then tells you yes, or how many months."

### 10. Privacy Center (3:00 to 3:15) · Privacy
- **Do:** **Privacy** tab → scroll to **Cloud log**: the two calls with their payloads. Show the **Cloud AI** switch. Point at **Delete all local data** (do not press).
- **Say:** "Every cloud call is logged on the device with its exact payload. One switch turns cloud AI off completely, and one button deletes everything."

### 11. Close (3:15 to 3:25)
- **Say:** "FinSight is a privacy-first AI financial companion: a verified picture of your money, and a coach that tells you how you're doing. Your data stays on the phone; only knowledge comes from the cloud. Rules verify, you decide."

---

## Backup plan

| Problem | What to do on stage | Line to say |
|---|---|---|
| Venue Wi-Fi down at step 8 | Switch phone to the hotspot (saved network). | "Let me use my hotspot." |
| Hotspot also fails, or Gemini errors / times out (9 s) | Ask anyway. The answer comes back `On-device` with the note "Cloud unavailable / Offline — answered on-device". Then show the backup video of the hybrid step. | "That's the router doing its job: no network, you still get an answer on the device. Here's the hybrid path from our rehearsal." |
| Health answer shows `On-device` with "names a person, merchant or number" | A merchant name matched a word in the question. Re-ask: "How healthy are my finances?" | "It held that one back because it looked personal. That's deliberate." |
| `Hybrid` badge never appears even online | Check Privacy: cloud switch ON, model line shown (key present). | Go on with the on-device answer. |
| Live notification does not appear | Operator: check `$ADB devices`; re-grant listener (command in checklist); repost with a new TID. Or use **hexagon → Add transaction** with "careem 650 card". | "Let me add one by hand, same pipeline." |
| Notification lands as "dup ×2" on an existing row | That TID was already used. | "And it noticed we've seen that payment before: one record, two pieces of evidence." |
| App was killed, everything is empty | Hexagon → **Import messages** (5 s), continue. | "Fresh phone, here are 78 bank messages." |
| Phone dies or mirroring fails | Play `~/Desktop/finsight-demo.mp4` from the laptop and narrate the same script. | |

## Do not

- Do not press **Import** twice: repeated messages merge as duplicates and the counts change.
- Do not press **Delete all local data** until the very end, if at all.
- Do not claim Gemma is running on the phone. Say "on-device rules today; on-device Gemma for the long tail is next".
- Do not quote a number you have not read off the screen.
