/* พฤติกรรมฝั่งเบราว์เซอร์ของ Foodlen: ตัวเลือกเมนูแบบค้นหา + สรุปพลังงานของมื้อที่เลือกแบบทันที
 * ทำงานเฉพาะหน้าที่มี [data-picker] หรือ #mealPreview */
(function () {
  "use strict";

  function norm(s) { return (s || "").toLowerCase().replace(/\s+/g, " ").trim(); }

  function matchesFilter(label, f) {
    if (!f) { return true; }
    var d = label.dataset, groups = " " + (d.groups || "") + " ", tags = " " + (d.tags || "") + " ";
    switch (f) {
      case "drink": return groups.indexOf(" milk ") >= 0 || /แก้ว|กล่อง|ขวด|หลอด/.test(d.serving || "");
      case "fruit": return groups.indexOf(" fruit ") >= 0 && tags.indexOf(" sweet ") < 0;
      case "sweet": return tags.indexOf(" sweet ") >= 0;
      case "fried": return tags.indexOf(" fried ") >= 0;
      default: return groups.indexOf(" " + f + " ") >= 0;
    }
  }

  function initPicker(root) {
    var search = root.querySelector(".picker-search");
    var chips = Array.prototype.slice.call(root.querySelectorAll(".chip"));
    var items = Array.prototype.slice.call(root.querySelectorAll(".pick"));
    var filter = "";

    function apply() {
      var q = norm(search.value), shown = 0;
      items.forEach(function (l) {
        var ok = (!q || norm(l.dataset.name).indexOf(q) >= 0) && matchesFilter(l, filter);
        l.hidden = !ok;
        if (ok) { shown++; }
      });
      root.classList.toggle("empty", shown === 0);
    }
    search.addEventListener("input", apply);
    chips.forEach(function (c) {
      c.addEventListener("click", function () {
        filter = c.dataset.filter;
        chips.forEach(function (x) { x.classList.toggle("on", x === c); });
        apply();
      });
    });
    // กด Enter ในช่องค้นหาแล้วเลือกรายการแรกที่เห็น (ไม่ส่งฟอร์มโดยไม่ตั้งใจ)
    search.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        e.preventDefault();
        var first = items.filter(function (l) { return !l.hidden; })[0];
        if (first) { first.querySelector("input").checked = true; first.querySelector("input").dispatchEvent(new Event("change", { bubbles: true })); }
      }
    });
    var checked = root.querySelector("input:checked");
    if (checked) { checked.closest(".pick").scrollIntoView({ block: "nearest" }); }
  }

  function fmt(n) { return Math.round(n).toLocaleString("th-TH"); }

  function initPreview(box) {
    var form = box.closest("form");
    if (!form) { return; }
    var daily = parseFloat(box.dataset.daily);
    function set(k, v) { var el = box.querySelector('[data-pv="' + k + '"]'); if (el) { el.textContent = v; } }

    function update() {
      var menu = form.querySelector('input[name="menuCode"]:checked');
      var portionEl = form.querySelector('input[name="portion"]:checked');
      if (!menu) { box.hidden = true; return; }
      var label = menu.closest("label"), d = label ? label.dataset : {};
      var kcal = parseFloat(d.kcal), portion = portionEl ? parseFloat(portionEl.value) : 1;
      box.hidden = false;
      set("name", d.label || (label ? label.textContent.trim() : ""));
      set("serving", d.serving || "1 หน่วยบริโภค");
      if (isNaN(kcal)) {
        set("kcal", "ไม่มีข้อมูล"); set("total", "ประเมินพลังงานไม่ได้"); set("share", "");
        return;
      }
      set("kcal", fmt(kcal));
      set("total", "≈ " + fmt(kcal * portion) + " kcal");
      set("share", isNaN(daily) ? "" : "(" + Math.round(kcal * portion / daily * 100) + "% ของที่ควรได้รับต่อวัน)");
    }
    form.addEventListener("change", function (e) {
      if (e.target.name === "menuCode" || e.target.name === "portion") { update(); }
    });
    update();
  }

  document.addEventListener("DOMContentLoaded", function () {
    Array.prototype.forEach.call(document.querySelectorAll("[data-picker]"), initPicker);
    var box = document.getElementById("mealPreview");
    if (box) { initPreview(box); }
  });
})();
