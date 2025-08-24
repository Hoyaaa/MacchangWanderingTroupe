// functions/.eslintrc.cjs
module.exports = {
  root: true,
  env: { node: true, es2022: true },
  parserOptions: { ecmaVersion: "latest", sourceType: "module" }, // ESM 코드 린트
  extends: ["eslint:recommended"],
  rules: {
    "no-unused-vars": ["warn", { args: "after-used", ignoreRestSiblings: true }]
  }
};
