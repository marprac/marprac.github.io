// Keep a useful error screen even when a browser cannot load the app module.
(function () {
  "use strict";
  const main = document.querySelector("main");
  if (window.location.protocol === "file:") {
    main.innerHTML = '<div class="empty-state"><h1>Open the online study room</h1><p>The file browser and document reader need a website connection. Open the published site below, or serve this folder with a local web server.</p><a class="button primary" href="https://marprac.github.io/">Open ISP Study Room</a></div>';
    return;
  }
  import("./app.js?v=isp-4").catch(function () {
    main.innerHTML = '<div class="empty-state"><h1>The study room could not start</h1><p>A required file could not be loaded. Check your connection and try again.</p><button class="button primary" type="button">Reload study room</button></div>';
    main.querySelector("button").addEventListener("click",function () { window.location.reload(); });
  });
})();
