// 푸시 알림만 받는 서비스 워커. 캐시나 오프라인은 하지 않는다 (ADR 004).
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  let data = { title: "CUK 알리미", body: "", url: "/", tag: "" };
  try {
    data = { ...data, ...event.data.json() };
  } catch (e) {
    // 본문이 없거나 JSON이 아니면 제목만 보여 준다.
  }
  event.waitUntil(
    self.registration.showNotification(data.title, {
      body: data.body,
      tag: data.tag || undefined,
      icon: "/icon-180.png",
      badge: "/icon-180.png",
      data: { url: data.url },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      for (const client of list) {
        if ("focus" in client) return client.focus().then(() => ("navigate" in client ? client.navigate(url) : undefined));
      }
      return self.clients.openWindow(url);
    }),
  );
});
