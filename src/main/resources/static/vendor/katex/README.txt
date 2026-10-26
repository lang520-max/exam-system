KaTeX 0.18.7 (MIT License)
来源：npm 包 katex@0.18.7 的 dist/ 目录
https://katex.org/  ·  https://github.com/KaTeX/KaTeX

本目录内容为第三方文件，未经修改，仅做本地化（离线可用）。

裁剪说明：dist/fonts 下原有 60 个字体文件（woff2/woff/ttf 三份，共约 1 MB）。
这里只保留了 20 个 .woff2（约 254 KB）——katex.min.css 的 @font-face
把 woff2 排在 src 列表第一位，现代浏览器会命中 woff2 而不会请求 woff/ttf，
因此不存在 404，也省下约 800 KB 仓库体积。

升级方式：npm pack katex@<版本>，用 dist/ 覆盖本目录，
并同样只保留 fonts/*.woff2。
