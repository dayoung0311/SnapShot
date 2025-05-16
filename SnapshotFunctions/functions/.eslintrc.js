module.exports = {
  root: true,
  env: {
    es6: true,
    node: true,
  },
  extends: [
    "eslint:recommended",
    "plugin:import/errors",
    "plugin:import/warnings",
    "plugin:import/typescript",
    "google",
    "plugin:@typescript-eslint/recommended",
  ],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: ["tsconfig.json", "tsconfig.dev.json"],
    sourceType: "module",
    ecmaVersion: 2020, // 또는 사용 중인 JS 버전에 맞게
  },
  ignorePatterns: [
    "/lib/**/*", // Ignore built files.
  ],
  plugins: [
    "@typescript-eslint",
    "import",
  ],
  rules: {
    "quotes": ["warn", "double"], // 큰따옴표 사용 (경고 수준)
    "comma-dangle": "off",
    "import/no-unresolved": 0,
    "indent": "off", // 들여쓰기 규칙 끔 (매우 많은 오류 발생 시)
    "object-curly-spacing": "off", // 객체 괄호 공백 규칙 끔
    "no-trailing-spaces": "off", // 줄 끝 공백 규칙 끔
    "eol-last": "off", // 파일 끝 개행 규칙 끔
    "max-len": ["warn", { "code": 120, "ignoreComments": true, "ignoreUrls": true, "ignoreStrings": true, "ignoreTemplateLiterals": true }], // 줄 길이 경고 수준으로, 120자로 완화 및 특정 요소 무시
    "@typescript-eslint/no-unused-vars": ["warn", { "argsIgnorePattern": "^_" }], // 사용하지 않는 변수 (언더스코어 시작 시) 경고 수준
    "operator-linebreak": "off", // 연산자 줄바꿈 규칙 끔
    "require-jsdoc": "off", // JSDoc 주석 강제 규칙 끔
    "valid-jsdoc": "off", // JSDoc 주석 유효성 검사 규칙 끔
    "spaced-comment": "warn", // 주석 공백 경고 수준
    "no-multiple-empty-lines": ["warn", { "max": 2 }], // 여러 빈 줄 경고 수준
    "arrow-parens": "off", // 화살표 함수 괄호 규칙 끔
    "linebreak-style": "off", // 줄바꿈 스타일 규칙 끔
    "no-tabs": "off", // 탭 사용 금지 규칙 끔
    "no-mixed-spaces-and-tabs": "off", // 공백과 탭 혼용 금지 규칙 끔
    "new-cap": "off", // 생성자 함수 대문자 시작 규칙 끔 (가끔 라이브러리에서 문제 발생)
    "no-prototype-builtins": "warn", // 프로토타입 내장 메소드 사용 경고 수준
    "@typescript-eslint/no-explicit-any": "warn", // 'any' 타입 사용 경고 수준
    "@typescript-eslint/explicit-module-boundary-types": "warn", // 모듈 경계 타입 명시 경고 수준
  },
};