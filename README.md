<div align="center">

# Pause PD

**تمرير طاقة الشاحن إلى الهاتف مباشرةً، وإبقاء البطارية خاملة.**

[![Platform](https://img.shields.io/badge/platform-Android%2010%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com)
[![Root](https://img.shields.io/badge/root-KernelSU%20%7C%20Magisk-orange?style=flat-square)](#المتطلبات)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)

</div>

---

## ما هذا؟

Pause PD تطبيق صغير يفعّل ميزة **Pass-through** في نواة سامسونج — المعروفة في One UI باسم *Pause USB PD* — على روم AOSP مثل Evolution X، حيث لا توجد واجهة Game Booster التي تتحكم بها.

عند التفعيل يتوقف الشحن: طاقة الشاحن تذهب إلى الهاتف مباشرة، والبطارية تبقى في مكانها — لا تُشحن ولا تُفرَّغ. الحرارة تقل، والإجهاد على البطارية يقل أثناء الألعاب أو الاستخدام الطويل تحت الشاحن.

> اختُبر على Galaxy S23 Ultra (SM-S918B) بنواة Evolution X 16 / أندرويد 16. التيار هبط من **3.1 A** أثناء الشحن إلى **0.3 A** في وضع `BYPASS_MODE`.

---

## لماذا؟

| | شحن عادي | إيقاف الشحن | Pause PD |
|---|---|---|---|
| مصدر طاقة الهاتف | البطارية والشاحن معًا | البطارية فقط | الشاحن مباشرة |
| البطارية | تُشحن | تُفرَّغ | خاملة |
| الحرارة | مرتفعة | مرتفعة (دورة تفريغ) | الأقل |
| النسبة خلال ساعات | ترتفع | تنخفض | ثابتة |

«إيقاف الشحن» ليس حلاً: الهاتف يظل يعمل من البطارية، فتنقص النسبة وترتفع الحرارة. الباسثرو يفصل البطارية عن المسار ويجعل الشاحن هو مصدر الطاقة الوحيد.

---

## كيف يعمل

```
شاحن PPS ──VBUS──▶ PMIC ──▶ VSYS (نظام الهاتف)
                     │
                     └──▶ البطارية   ← يُغلق هذا المسار في وضع pass-through
```

1. التطبيق يكتب `1` في `/sys/class/power_supply/battery/pass_through`.
2. النواة تنتظر ٥ ثوانٍ ثم تتحقق من الشروط: شاحن PD APDO/PPS، حالة `CHARGING`، بطارية فوق ٢٠٪.
3. عند النجاح تتحول `direct_charging_chg_status` إلى `BYPASS_MODE` خلال ٩ ثوانٍ تقريبًا من الطلب.

`BYPASS_MODE` هي إشارة التفعيل الحقيقية؛ التطبيق لا يعرض «مفعّل» قبلها أبدًا.

التفاصيل الكاملة — جدول العقد sysfs، آلة الحالة، والقياسات — في [`docs/THEORY.md`](docs/THEORY.md).

---

## المتطلبات

- هاتف سامسونج بنواة تحتوي عقدة `pass_through` (اختُبر على S23 Ultra).
- **Root** — KernelSU أو Magisk. لا حاجة لـ SELinux permissive.
- **شاحن PD APDO / PPS** — الشواحن العادية واللاسلكية لا تعمل.
- بطارية **أعلى من ٢٠٪** وحالة «يشحن» عند التفعيل.
- أندرويد 10 أو أحدث (minSdk 29).

---

## التثبيت

أو نزّل الـAPK جاهز من [Releases](https://github.com/Akramx15/pause-pd/releases/latest).

من المصدر:

```bash
git clone https://github.com/Akramx15/pause-pd.git
cd pause-pd
./build.sh
adb install -r dist/pause-pd-debug.apk
```

ثم:

1. افتح **KernelSU Next ← Superuser** وأضف **Pause PD** وامنحه root. (KernelSU Next يخفي `su` عن التطبيقات غير الممنوحة، فلا يظهر طلب تلقائي.)
2. وصّل شاحن PPS.
3. اضغط **تفعيل** وانتظر ~٩ ثوانٍ.

لا يحتاج التطبيق أي صلاحية إضافية غير root، إلا إذا فعّلت مراقبة الألعاب فهي تطلب صلاحية «الوصول إلى الاستخدام».

---

## الاستخدام

**الشاشة الرئيسية** — بطاقة حالة كبيرة، زر تبديل، شروط التفعيل الثلاثة، وقياسات حية كل ثانيتين:

| الحالة | المعنى |
|---|---|
| متوقف | لا طلب تفعيل حالي |
| جاري التفعيل | تم إرسال الطلب، النواة لم تدخل `BYPASS_MODE` بعد |
| مفعّل | `BYPASS_MODE` مؤكدة — البطارية خاملة |
| الشاحن غير مدعوم | الشاحن موصول لكنه ليس PPS |
| البطارية منخفضة | النسبة ٢٠٪ أو أقل |
| خطأ | العقدة غير موجودة أو لا يوجد root |

**بلاطة الإعدادات السريعة** — أضفها من لوحة QS، الضغط عليها يبدّل الحالة، والوصف الفرعي يحمل الحالة أو سبب الفشل.

**الإعدادات**

- *إعادة الضبط عند فصل الشاحن* (افتراضي: مفعّل) — يكتب `0` عند الفصل حتى لا يبقى الطلب القديم فيُفعّل نفسه على أول شاحن PPS لاحق.
- *التفعيل التلقائي أثناء الألعاب* (افتراضي: مغلق) — يفعّل الباسثرو عند دخول لعبة من قائمتك مع شاحن موصول، ويعيده عند الخروج. يحتاج صلاحية الوصول إلى الاستخدام، ويعمل عبر خدمة أمامية خفيفة.

---

## الأمان

- التطبيق لا يقرأ ولا يرسل أي بيانات. لا يوجد اتصال شبكي.
- الكتابة الوحيدة في نظام الملفات هي عقدة `pass_through` (وكذلك `0` عند الإقلاع والفصل).
- الباسثرو لا يشحن البطارية: في جلسة طويلة ستبقى النسبة كما هي، فاحرص على الشحن قبل البدء.
- استخدم كابلًا وشاحنًا جيدين؛ التيار يمر عبر المنفذ بشكل مباشر.
- الـ root مسؤوليتك، وكذلك أي تعديل على النواة.

---

## الأسئلة الشائعة

**هل يشحن البطارية؟**
لا. هذه هي الفكرة: الهاتف يعمل من الشاحن والبطارية خاملة.

**ضغطت تفعيل ولم يحدث شيء.**
تحقق من: وجود شاحن PPS (`battery/online` = 38 أو 41)، حالة `Charging`، النسبة فوق ٢٠٪، ومنح صلاحية root. الشاشة تعرض سبب الفشل مباشرة.

**لماذا بقي مفعّلًا بعد فصل الشاحن؟**
لأن `pass_through` يبقى `1` في النواة بعد الفصل، ويُفعّل نفسه تلقائيًا عند وصل شاحن PPS في المرة القادمة. التطبيق يكتب `0` عند الفصل وعند الإقلاع — أبقِ خيار «إعادة الضبط عند فصل الشاحن» مفعّلًا.

**هل يعمل على أجهزة أخرى؟**
على أجهزة سامسونج التي تحتوي العقدة نفسها نعم من حيث المبدأ. على أي جهاز آخر ستظهر «خطأ» بدون انهيار.

**هل يستهلك بطارية في الخلفية؟**
لا. لا شيء يعمل في الخلفية إلا إذا فعّلت مراقبة الألعاب، وهي تفحص كل ١٥ ثانية أثناء وجود شاحن فقط.

**هل أحتاج SELinux permissive؟**
لا. كل الكتابات تمر عبر `su`.

**هل يعمل مع الشحن اللاسلكي؟**
لا. اللاسلكي لا يقدّم PD APDO/PPS.

**هل يتعارض مع شحن 45 واط؟**
لا. الباسثرو يعمل ضمن تفاوض PD القائم؛ التيار المار يعتمد على الشاحن والكابل.

---

## لقطات الشاشة

<p align="center">
  <img src="docs/screenshots/main.png" width="280" alt="الشاشة الرئيسية">
</p>

---

## بنية المشروع

> تفاصيل البناء والتوقيع: [`BUILD.md`](BUILD.md).

```
app/src/main/
├── AndroidManifest.xml
├── java/dev/pausepd/
│   ├── PassThrough.kt      # جسر sysfs عبر su، القراءة والكتابة والتحقق
│   ├── MainActivity.kt     # الواجهة والقياسات الحية
│   ├── SettingsActivity.kt # إعدادات الفصل ومراقبة الألعاب
│   ├── TileService.kt      # بلاطة QS
│   ├── BootReceiver.kt     # تصفير دفاعي عند الإقلاع
│   ├── PowerReceiver.kt    # تصفير عند فصل الشاحن
│   ├── GameWatch.kt        # تتبع التطبيق الأمامي
│   ├── GameWatchService.kt # خدمة المراقبة الاختيارية
│   └── Prefs.kt
└── res/                    # التخطيطات، النصوص (ar/en)، الأيقونات
docs/THEORY.md              # النظرية الكاملة والقياسات
```

---

## شكر وتقدير

- نواة سامسونج المفتوحة، التي تحتوي منطق `pass_through` و`direct_charging`.
- [KernelSU](https://kernelsu.org) و[Magisk](https://github.com/topjohnwu/Magisk) — من دونهم لا وصول إلى العقدة.
- [Evolution X](https://evolution-x.org) — روم نظيف بلا طبقة Game Booster.

---

## الترخيص

[MIT](LICENSE) © 2026 Akram

---

## English

**Pause PD** toggles Samsung's kernel pass-through switch (`Pause USB PD`) on AOSP ROMs, where Game Booster's UI is missing. While engaged the charger powers the phone directly and the battery sits idle — no charge, no drain, less heat.

- **Requirements:** Samsung device exposing `battery/pass_through`, root (KernelSU/Magisk), a PD APDO/PPS charger, battery above 20%, Android 12+.
- **How:** writes `1` to the sysfs node through `su`; the kernel waits 5 s, validates the charger, then reports `BYPASS_MODE` in `direct_charging_chg_status` (~9 s). The UI only shows *Active* on `BYPASS_MODE`.
- **Safety:** the app writes `0` on boot and on unplug so a stale request cannot auto-engage on the next PPS charger.
- **Tested:** Galaxy S23 Ultra on Evolution X 16 — current dropped from 3.1 A to 0.3 A in `BYPASS_MODE`.
- **Build:** `./gradlew assembleDebug` — Kotlin stdlib only, no AndroidX, no third-party libraries.
- **License:** MIT © 2026 Akram. Full theory in [`docs/THEORY.md`](docs/THEORY.md).
