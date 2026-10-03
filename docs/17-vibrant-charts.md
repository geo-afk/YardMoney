# Pilot 0.3.6 — vibrant charts

Charts now use a dedicated emerald, blue and violet palette instead of the muted primary, secondary and tertiary colors used for general controls. Needs, Wants and Savings retain consistent hues on Home and Plan; report charts share the palette. Light themes use deeper saturated colors, while Dark and AMOLED use brighter colors. Chart marks are adjusted against the theme's highest container surface to maintain at least 3:1 contrast. This also works with wallpaper-based themes.

Remaining allocation arcs use 38% color opacity, up from 18%, so a newly funded budget is more colorful before spending. Solid arcs still indicate used money, with exact figures and labels preserving the distinction. Activity columns use full color throughout; the selected interval keeps its underline and selected accessibility description.

A host regression check verifies distinct category colors and chart-mark contrast on the standard Light, Dark and AMOLED surfaces. Existing chart, ledger and interaction tests remain applicable. Financial calculations and storage are unchanged.

## Validation

Full verification passed on 2 October 2026: 73 host tests and 38 instrumentation tests on the connected Pixel 8 Pro, with zero failures, errors or skipped device tests. Debug and optimized unsigned release builds passed; lint reported zero errors and 34 warnings. The installable debug APK passed signature verification with the same development signer as 0.3.5 and 16KB ZIP alignment. Device instrumentation installed 0.3.6 over the prior version. Manual visual checks across the broader device and wallpaper matrix remain pending.

Version: 0.3.6, code 10. Database schema remains version 1.
