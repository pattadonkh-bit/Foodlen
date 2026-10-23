/* วาดกราฟด้วย Chart.js จากข้อมูล JSON ของ /api/...
 * ใช้: <canvas data-chart="ชื่อกราฟ" data-url="/api/..."></canvas> */
(function () {
  "use strict";
  if (typeof Chart === "undefined") { return; }

  var C = {
    green: "#1f9d6b", greenSoft: "rgba(31,157,107,.16)", red: "#e5484d", redSoft: "rgba(229,72,77,.16)",
    yellow: "#f5a524", yellowSoft: "rgba(245,165,36,.2)", blue: "#3b82f6", blueSoft: "rgba(59,130,246,.14)",
    grey: "#aab4ad", ink: "#1f2d26"
  };
  var LEVEL = { RED: C.red, YELLOW: C.yellow, GREEN: "#2e9e5b", UNKNOWN: C.grey };
  var STATUS = { SEVERE_THINNESS: "#2563eb", THINNESS: "#60a5fa", NORMAL: "#2e9e5b", OVERWEIGHT: C.yellow, OBESITY: C.red, NONE: C.grey };
  var SOURCE = { AI_AUTO: "#2e9e5b", AI_CONFIRMED: C.blue, MANUAL: C.grey };

  Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
  Chart.defaults.font.size = 13;
  Chart.defaults.color = "#4f6157";
  Chart.defaults.plugins.legend.labels.usePointStyle = true;
  Chart.defaults.plugins.legend.labels.boxWidth = 8;
  Chart.defaults.plugins.tooltip.padding = 10;
  Chart.defaults.plugins.tooltip.cornerRadius = 10;
  Chart.defaults.maintainAspectRatio = false;

  var cache = {};
  function load(url) {
    if (!cache[url]) { cache[url] = fetch(url).then(function (r) { if (!r.ok) { throw new Error(r.status); } return r.json(); }); }
    return cache[url];
  }
  function empty(canvas, msg) {
    var d = document.createElement("div");
    d.className = "empty-chart";
    d.textContent = msg || "ยังไม่มีข้อมูลสำหรับแสดงกราฟ";
    canvas.parentNode.replaceChild(d, canvas);
  }
  function total(list) { return list.reduce(function (a, x) { return a + x.value; }, 0); }
  function years(v) { return (v % 12 === 0) ? (v / 12) + " ปี" : ""; }

  var draw = {};

  /* ---------- กราฟการเจริญเติบโต: BMI ตามอายุ พร้อมเส้นเกณฑ์ WHO ---------- */
  draw.growth = function (canvas, d) {
    if (!d.points.length) { return empty(canvas, "ยังไม่มีข้อมูลการวัด"); }
    var cv = {}; d.curves.forEach(function (c) { cv[c.key] = c; });
    function line(key, color, fill, extra) {
      var c = cv[key];
      return Object.assign({
        label: c.label, data: d.months.map(function (m, i) { return { x: m, y: c.values[i] }; }),
        borderColor: color, borderWidth: 1.4, pointRadius: 0, pointHoverRadius: 0, fill: fill, tension: .25, order: 5
      }, extra || {});
    }
    var datasets = [
      line("m3", C.blue, false),
      line("m2", "#6aa5ff", "-1", { backgroundColor: C.blueSoft }),
      line("p1", "#8bd16a", "-1", { backgroundColor: C.greenSoft }),
      line("p2", C.yellow, "-1", { backgroundColor: C.yellowSoft }),
      line("p3", C.red, "-1", { backgroundColor: C.redSoft }),
      line("median", "#2e9e5b", false, { borderDash: [6, 5], borderWidth: 1.6 }),
      {
        label: "เด็กคนนี้", order: 1,
        data: d.points.map(function (p) { return { x: p.ageMonths, y: p.bmi, p: p }; }),
        borderColor: C.ink, backgroundColor: "#fff", borderWidth: 2.5, pointRadius: 7, pointHoverRadius: 9,
        pointBorderWidth: 3, pointBorderColor: C.ink, tension: .2, fill: false
      }
    ];
    var lo = Math.ceil(d.months[0] / 12) * 12, hi = Math.floor(d.months[d.months.length - 1] / 12) * 12;
    new Chart(canvas, {
      type: "line", data: { datasets: datasets },
      options: {
        interaction: { mode: "nearest", intersect: true },
        scales: {
          x: { type: "linear", min: lo, max: hi, ticks: { stepSize: 12, callback: years }, title: { display: true, text: "อายุ" }, grid: { color: "#eef3ef" } },
          y: { title: { display: true, text: "BMI (กก./ม.²)" }, grid: { color: "#eef3ef" } }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            filter: function (i) { return i.datasetIndex === 6; },
            callbacks: {
              title: function (items) { var p = items[0].raw.p; return p.date + " · อายุ " + Math.floor(p.ageMonths / 12) + " ปี " + (p.ageMonths % 12) + " เดือน"; },
              label: function (item) {
                var p = item.raw.p;
                return ["BMI " + p.bmi + " (z = " + p.z + ")", "ภาวะโภชนาการ: " + p.status, "น้ำหนัก " + p.weightKg + " กก. · ส่วนสูง " + p.heightCm + " ซม."];
              }
            }
          }
        }
      }
    });
  };

  /* ---------- น้ำหนัก/ส่วนสูงตามเวลา ---------- */
  draw.measures = function (canvas, d) {
    if (d.points.length < 2) { return empty(canvas, "บันทึกการวัดอย่างน้อย 2 ครั้งเพื่อดูแนวโน้ม"); }
    new Chart(canvas, {
      type: "line",
      data: {
        labels: d.points.map(function (p) { return p.date; }),
        datasets: [
          { label: "น้ำหนัก (กก.)", data: d.points.map(function (p) { return p.weightKg; }), borderColor: C.green, backgroundColor: C.greenSoft, fill: true, tension: .3, yAxisID: "y", pointRadius: 5 },
          { label: "ส่วนสูง (ซม.)", data: d.points.map(function (p) { return p.heightCm; }), borderColor: C.blue, tension: .3, yAxisID: "y1", pointRadius: 5 }
        ]
      },
      options: {
        scales: {
          y: { position: "left", title: { display: true, text: "กก." }, grid: { color: "#eef3ef" } },
          y1: { position: "right", title: { display: true, text: "ซม." }, grid: { drawOnChartArea: false } }
        },
        plugins: { legend: { position: "bottom" } }
      }
    });
  };

  /* ---------- รายสัปดาห์ ---------- */
  draw.weeklyKcal = function (canvas, d) {
    if (d.kcal.every(function (v) { return !v; })) { return empty(canvas, "ยังไม่มีพลังงานที่บันทึกในสัปดาห์นี้"); }
    var ds = [{ type: "bar", label: "พลังงานที่ได้รับ (kcal)", data: d.kcal, backgroundColor: "rgba(31,157,107,.78)", borderRadius: 8, maxBarThickness: 46 }];
    if (d.target) {
      ds.push({ type: "line", label: "พลังงานอ้างอิงต่อวัน (" + d.target + ")", data: d.labels.map(function () { return d.target; }), borderColor: C.red, borderDash: [6, 5], borderWidth: 2, pointRadius: 0 });
    }
    new Chart(canvas, {
      data: { labels: d.labels, datasets: ds },
      options: { scales: { y: { beginAtZero: true, title: { display: true, text: "kcal" }, grid: { color: "#eef3ef" } }, x: { grid: { display: false } } }, plugins: { legend: { position: "bottom" } } }
    });
  };

  function stacked(canvas, labels, rows, horizontal) {
    var sets = [["red", "แดง", C.red], ["yellow", "เหลือง", C.yellow], ["green", "เขียว", "#2e9e5b"], ["unknown", "ประเมินไม่ได้", C.grey]];
    new Chart(canvas, {
      type: "bar",
      data: {
        labels: labels,
        datasets: sets.map(function (s) { return { label: s[1], data: rows[s[0]], backgroundColor: s[2], borderRadius: 4, borderSkipped: false }; })
      },
      options: {
        indexAxis: horizontal ? "y" : "x",
        scales: {
          x: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { display: !horizontal ? false : true, color: "#eef3ef" } },
          y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { display: horizontal ? false : true, color: "#eef3ef" } }
        },
        plugins: { legend: { position: "bottom" } }
      }
    });
  }

  draw.weeklyLevels = function (canvas, d) {
    var sum = d.red.concat(d.yellow, d.green, d.unknown).reduce(function (a, b) { return a + b; }, 0);
    if (!sum) { return empty(canvas, "ยังไม่มีมื้ออาหารที่บันทึก"); }
    stacked(canvas, d.labels, { red: d.red, yellow: d.yellow, green: d.green, unknown: d.unknown }, false);
  };

  function doughnut(canvas, items, colorOf, cutout) {
    if (!total(items)) { return empty(canvas); }
    new Chart(canvas, {
      type: "doughnut",
      data: {
        labels: items.map(function (i) { return i.label; }),
        datasets: [{ data: items.map(function (i) { return i.value; }), backgroundColor: items.map(function (i) { return colorOf[i.code] || C.grey; }), borderColor: "#fff", borderWidth: 3, hoverOffset: 6 }]
      },
      options: { cutout: cutout || "62%", plugins: { legend: { position: "right", labels: { padding: 12 } } } }
    });
  }

  draw.weeklyDonut = function (canvas, d) {
    var items = [
      { code: "RED", label: "แดง", value: d.redTotal }, { code: "YELLOW", label: "เหลือง", value: d.yellowTotal },
      { code: "GREEN", label: "เขียว", value: d.greenTotal }, { code: "UNKNOWN", label: "ประเมินไม่ได้", value: d.unknownTotal }
    ];
    doughnut(canvas, items, LEVEL);
  };

  /* ---------- สถิติรวม ---------- */
  draw.statusDonut = function (canvas, d) { doughnut(canvas, d.statusDistribution, STATUS); };
  draw.levelDonut = function (canvas, d) { doughnut(canvas, d.levelDistribution, LEVEL); };
  draw.sourceDonut = function (canvas, d) { doughnut(canvas, d.sourceDistribution, SOURCE); };

  draw.topMenus = function (canvas, d) {
    if (!d.topMenus.length) { return empty(canvas); }
    new Chart(canvas, {
      type: "bar",
      data: { labels: d.topMenus.map(function (m) { return m.label; }), datasets: [{ label: "จำนวนมื้อ", data: d.topMenus.map(function (m) { return m.value; }), backgroundColor: "rgba(31,157,107,.8)", borderRadius: 8 }] },
      options: { indexAxis: "y", scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: "#eef3ef" } }, y: { grid: { display: false } } }, plugins: { legend: { display: false } } }
    });
  };

  draw.levelByStatus = function (canvas, d) {
    if (!d.levelByStatus.length) { return empty(canvas); }
    var r = d.levelByStatus;
    stacked(canvas, r.map(function (x) { return x.label; }),
      { red: r.map(function (x) { return x.red; }), yellow: r.map(function (x) { return x.yellow; }), green: r.map(function (x) { return x.green; }), unknown: r.map(function (x) { return x.unknown; }) }, true);
  };

  draw.days14 = function (canvas, d) {
    var r = d.days;
    var sum = r.reduce(function (a, x) { return a + x.red + x.yellow + x.green + x.unknown; }, 0);
    if (!sum) { return empty(canvas); }
    stacked(canvas, r.map(function (x) { return x.label; }),
      { red: r.map(function (x) { return x.red; }), yellow: r.map(function (x) { return x.yellow; }), green: r.map(function (x) { return x.green; }), unknown: r.map(function (x) { return x.unknown; }) }, false);
  };

  /* ---------- มาตรวัดสัดส่วนพลังงานของมื้อ ---------- */
  document.querySelectorAll("canvas[data-gauge]").forEach(function (canvas) {
    var pct = parseFloat(canvas.getAttribute("data-gauge"));
    var color = canvas.getAttribute("data-color") || C.green;
    var shown = Math.min(Math.max(pct, 0), 100);
    new Chart(canvas, {
      type: "doughnut",
      data: { datasets: [{ data: [shown, 100 - shown], backgroundColor: [color, "#e8f0ea"], borderWidth: 0 }] },
      options: { rotation: -90, circumference: 180, cutout: "72%", plugins: { legend: { display: false }, tooltip: { enabled: false } } },
      plugins: [{
        id: "centerText",
        afterDraw: function (chart) {
          var a = chart.chartArea, ctx = chart.ctx;
          ctx.save(); ctx.textAlign = "center"; ctx.fillStyle = "#1f2d26";
          ctx.font = "700 30px " + Chart.defaults.font.family;
          ctx.fillText(Math.round(pct) + "%", (a.left + a.right) / 2, a.bottom - 30);
          ctx.font = "13px " + Chart.defaults.font.family; ctx.fillStyle = "#7b8a81";
          ctx.fillText("ของพลังงานต่อวัน", (a.left + a.right) / 2, a.bottom - 10);
          ctx.restore();
        }
      }]
    });
  });

  document.querySelectorAll("canvas[data-chart]").forEach(function (canvas) {
    var fn = draw[canvas.getAttribute("data-chart")];
    var url = canvas.getAttribute("data-url");
    if (!fn || !url) { return; }
    load(url).then(function (d) { fn(canvas, d); }).catch(function () { empty(canvas, "โหลดข้อมูลกราฟไม่สำเร็จ"); });
  });
})();
