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

  /* ถ่ายภาพจากกล้องในเบราว์เซอร์ (getUserMedia): ถ่ายแล้วใส่ไฟล์ JPEG ลงในช่อง input[type=file] ของฟอร์มเดิม
   * ภาพถูกย่อให้ด้านยาวไม่เกิน 1280 px ก่อนส่ง กล้องใช้ได้เฉพาะบน HTTPS หรือ localhost */
  var MAX_SIDE = 1280;

  function cameraError(err) {
    var name = err && err.name;
    if (name === "NotAllowedError" || name === "SecurityError") {
      return "ไม่ได้รับอนุญาตให้ใช้กล้อง กรุณากดอนุญาตที่ไอคอนกล้องหรือแม่กุญแจบนแถบที่อยู่ของเบราว์เซอร์ แล้วลองใหม่ (หรือกด “เลือกไฟล์ภาพ”)";
    }
    if (name === "NotFoundError" || name === "DevicesNotFoundError" || name === "OverconstrainedError") {
      return "ไม่พบกล้องในอุปกรณ์นี้ กรุณากด “เลือกไฟล์ภาพ” แทน";
    }
    if (name === "NotReadableError" || name === "TrackStartError") {
      return "กล้องกำลังถูกใช้งานโดยโปรแกรมอื่น กรุณาปิดโปรแกรมนั้นแล้วลองใหม่";
    }
    return "เปิดกล้องไม่สำเร็จ กรุณากด “เลือกไฟล์ภาพ” แทน";
  }

  function initCamera(root) {
    function part(k) { return root.querySelector('[data-cam="' + k + '"]'); }
    var input = root.querySelector('input[type="file"]');
    var openBtn = part("open"), panel = part("panel"), video = part("video");
    var shotBtn = part("shot"), switchBtn = part("switch"), closeBtn = part("close"), msg = part("msg");
    var form = root.closest("form");
    var stream = null, facing = "environment";

    function say(text) { msg.textContent = text || ""; msg.hidden = !text; }

    function stop() {
      if (stream) { stream.getTracks().forEach(function (t) { t.stop(); }); stream = null; }
      video.srcObject = null;
      panel.hidden = true;
    }

    var canCapture = typeof window.DataTransfer !== "undefined" && typeof window.File !== "undefined";
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !canCapture) {
      openBtn.disabled = true;
      say("เบราว์เซอร์นี้ไม่รองรับการเปิดกล้อง กรุณากด “เลือกไฟล์ภาพ” แทน");
    } else if (!window.isSecureContext) {
      openBtn.disabled = true;
      say("การเปิดกล้องในเบราว์เซอร์ใช้ได้เฉพาะเมื่อเปิดเว็บผ่าน HTTPS (หรือ localhost) ตอนนี้กรุณากด “เลือกไฟล์ภาพ” แทน");
    }

    function start() {
      say("");
      stop();
      return navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: facing }, width: { ideal: 1280 }, height: { ideal: 960 } },
        audio: false
      }).then(function (s) {
        stream = s;
        video.srcObject = s;
        panel.hidden = false;
        var p = video.play();
        if (p && p.catch) { p.catch(function () { }); }
        // มีกล้องมากกว่าหนึ่งตัว (เช่น มือถือ) จึงแสดงปุ่มสลับกล้อง
        return navigator.mediaDevices.enumerateDevices().then(function (devs) {
          switchBtn.hidden = devs.filter(function (d) { return d.kind === "videoinput"; }).length < 2;
        }).catch(function () { });
      }).catch(function (err) { say(cameraError(err)); });
    }

    function shoot() {
      var w = video.videoWidth, h = video.videoHeight;
      if (!w || !h) { say("กล้องยังไม่พร้อม รอสักครู่แล้วกดถ่ายภาพอีกครั้ง"); return; }
      var scale = Math.min(1, MAX_SIDE / Math.max(w, h));
      var canvas = document.createElement("canvas");
      canvas.width = Math.round(w * scale);
      canvas.height = Math.round(h * scale);
      canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
      canvas.toBlob(function (blob) {
        if (!blob) { say("ถ่ายภาพไม่สำเร็จ กรุณาลองใหม่"); return; }
        var file = new File([blob], "meal-" + Date.now() + ".jpg", { type: "image/jpeg" });
        var dt = new DataTransfer();
        dt.items.add(file);
        input.files = dt.files;
        input.dispatchEvent(new Event("change", { bubbles: true }));
        say("");
        stop();
        openBtn.textContent = "📷 ถ่ายใหม่";
      }, "image/jpeg", 0.9);
    }

    openBtn.addEventListener("click", start);
    shotBtn.addEventListener("click", shoot);
    closeBtn.addEventListener("click", stop);
    switchBtn.addEventListener("click", function () {
      facing = facing === "environment" ? "user" : "environment";
      start();
    });
    window.addEventListener("pagehide", stop);
    if (form) {
      form.addEventListener("submit", function (e) {
        if (!input.files || input.files.length === 0) {
          e.preventDefault();
          say("กรุณาถ่ายภาพหรือเลือกไฟล์ภาพก่อน");
          return;
        }
        stop();
      });
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    Array.prototype.forEach.call(document.querySelectorAll("[data-camera]"), initCamera);
    Array.prototype.forEach.call(document.querySelectorAll("[data-picker]"), initPicker);
    var box = document.getElementById("mealPreview");
    if (box) { initPreview(box); }
  });
})();
