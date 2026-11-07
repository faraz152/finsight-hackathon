#!/usr/bin/env bash
# Post N fake bank / wallet notifications to the connected phone via adb (FinSight live-capture test).
# Usage: scripts/send_test_sms.sh [N=5] [delay_seconds=1.5]
# The app's NotificationCaptureService accepts com.android.shell in DEBUG builds; grant access first:
#   adb shell cmd notification allow_listener com.finsight.app/com.finsight.android.NotificationCaptureService
# Samples mirror finsight-app/composeApp/src/commonMain/kotlin/com/finsight/data/TestSamples.kt;
# {TID} becomes 12 random digits per send so repeated samples are not merged as duplicates.
set -euo pipefail

N="${1:-5}"
DELAY="${2:-1.5}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"

# sender|text  (no single quotes in texts: the remote command is "..." with the text inside '...')
SAMPLES=(
  "8558|Rs 1,250.00 sent to Ali Raza JazzCash 0300****567 from your JazzCash account. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: {TID}."
  "8558|You have received Rs 3,000.00 from Hamza Khan 0321****890 in your JazzCash account. TID: {TID}. Balance: Rs 11,750.00"
  "Easypaisa|Rs 850.00 paid to Bykea via Easypaisa. Trx ID {TID}. Balance Rs 4,150.00"
  "HBL|Rs 25,000.00 received via Raast from SARA AHMED into your A/C **4821. Ref: {TID}"
  "HBL|Rs 5,000.00 sent to USMAN TARIQ via Raast from A/C **4821. Ref: {TID}"
  "HBL|Rs 2,890.00 spent at KFC Gulberg using HBL Debit Card **7710. Ref: {TID}. Avail Bal: Rs 65,200.00"
  "HBL|Rs 1,850.00 spent at Foodpanda using card **7710. Ref: {TID}. Avail Bal: Rs 71,640.00"
  "HBL|Rs 4,499.00 spent at DARAZ PK online using card **7710. Ref: {TID}. Avail Bal: Rs 67,141.00"
  "Meezan|Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD. Ref: {TID}. Avail Bal: Rs 212,450.00"
  "UBL|Cash withdrawal of Rs 10,000.00 from UBL ATM Clifton using card **3390. Ref: {TID}. Avail Bal: Rs 40,120.00"
  "HBL|Rs 8,450.00 paid to K-Electric bill via HBL Mobile. Consumer no 0400012345. Ref: {TID}"
  "8558|Rs 2,340.00 paid for SNGPL Bill via JazzCash. TID: {TID}. Balance: Rs 6,410.00"
  "Easypaisa|Rs 3,199.00 paid for PTCL Bill via Easypaisa. Trx ID {TID}. Balance Rs 951.00"
  "Jazz|Mobile load of Rs 500.00 for JAZZ PREPAID 0301****222 successful via JazzCash. TID: {TID}"
  "HBL|Rs 1,200.00 refund received from Daraz to card **7710. Ref: {TID}"
  "HBL|Your payment of Rs 2,500.00 at Cheezious has been reversed to card **7710. Ref: {TID}"
  "HBL|Rs 450.00 debited from A/C **4821 for SMS charges and excise duty. Ref: {TID}"
  "HBL|Your OTP for transaction of Rs 5,000.00 is 482913. Do not share it with anyone."
  "8558|Dear customer your JazzCash account will be blocked. Click link bit.ly/jc-kyc to update your KYC"
)

"$ADB" get-state >/dev/null || { echo "No device: check '$ADB devices'"; exit 1; }

tid() { printf '%06d%06d' $((RANDOM % 1000000)) $((RANDOM % 1000000)); }

for ((i = 0; i < N; i++)); do
  entry="${SAMPLES[$((RANDOM % ${#SAMPLES[@]}))]}"
  sender="${entry%%|*}"
  text="${entry#*|}"
  text="${text//\{TID\}/$(tid)}"
  tag="fs$(date +%s)$i"
  echo "[$((i + 1))/$N] $sender: $text"
  # Quote the WHOLE remote command (text in single quotes inside it), or the remote shell splits the text into words.
  "$ADB" shell "cmd notification post -S bigtext -t '$sender' $tag '$text'" >/dev/null
  if ((i + 1 < N)); then sleep "$DELAY"; fi
done
echo "Done. Watch the result: $ADB logcat -s FinSight"
