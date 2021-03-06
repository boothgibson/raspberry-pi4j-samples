## Bits and Pieces for WebComponents

- [W3C Reference](http://w3c.github.io/webcomponents/spec/custom/)
- [Shadow DOM Spec](https://w3c.github.io/webcomponents/spec/shadow/)
- [HTML imports](http://w3c.github.io/webcomponents/spec/imports/)
- [HTML templates](https://html.spec.whatwg.org/multipage/webappapis.html)

- [Tutorial](https://auth0.com/blog/web-components-how-to-craft-your-own-custom-components/)

---

Use nodeJS to run the pages:
 ```bash
 $ node server.js
```

Then load the pages in a browser, using for example [http://localhost:8080/component.01/index.html](http://localhost:8080/component.01/index.html).

---

The `WebComponents` standard allows the definition of custom reusable visual components, used in a web page like any standard component.

Example:
```hntml
<pluvio-meter id="pluviometer-01"
              title="m/m per hour"
              min-value="0"
              max-value="10"
              major-ticks="1"
              minor-ticks="0.25"
              value="0"
              width="60"
              height="220"></pluvio-meter>
```
In addition, they may be accessed from JavaScript:
```javascript
function setData() {
  let elem = document.getElementById("pluviometer-01");
  let value = document.getElementById("rain-value").value;
  elem.value = value;
}
```
---

More to come...
