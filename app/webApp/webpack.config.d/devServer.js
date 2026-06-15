// 프론트 dev 서버 포트를 8081로 고정한다(백엔드 8082와 분리해 포트 충돌 방지).
// Kotlin/Wasm(JS) 브라우저 타깃의 webpack dev 서버 설정을 config-cache 안전하게 덮어쓴다.
config.devServer = config.devServer || {};
config.devServer.port = 8081;
