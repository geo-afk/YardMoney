# Separate demo installation

Run `powershell -ExecutionPolicy Bypass -File .\scripts\install-demo.ps1` with the SDK, Java and an authorized Android device configured as in the testing guide. Add `-Serial YOUR_DEVICE_SERIAL` when more than one target is connected. The script builds debug APKs with `-PdemoInstall=true`, installs them and opens YardMoney Demo.

The demo uses application ID `jm.yardmoney.demo`, its own encrypted database and a separate launcher label. The ordinary app remains `jm.yardmoney`. Builds without that Gradle property keep the ordinary package and AndroidJUnitRunner. The demo data installer resides only in the test APK, requires the demo package, and is not discovered as a regular test. It never writes dummy records into the ordinary application.

The fixture is relative to the current Jamaica date. It includes 180 days of two daily expenses, monthly salary and freelance income, cash/wallet funding, linked refunds, savings transfers and goal contributions, positive and negative adjustments, recurring bills, an overdue partially paid bill, debt and savings reservations, category limits, itemized fictional receipts with price observations, draft receipts and three shopping lists. The current period covers the last fourteen days. All names are visibly marked Demo.

Loading uses one database transaction through the same repository validation used by the application. A populated demo database is retained on rerun; there is no implicit reset. To start over, uninstall **YardMoney Demo** from the phone and rerun the script. Uninstalling that copy deletes only its own demo records.

On 2 October 2026, the connected Pixel 8 Pro received the normal 0.3.6 APK and the separate demo copy. The loader verified 434 transactions, four accounts, 12 scheduled reservation occurrences, three goals, 18 receipts and 24 shopping items. A second run returned the same counts without duplicating data. App launch was checked separately. These are seeded-data installation checks, not a new full instrumentation suite result.
