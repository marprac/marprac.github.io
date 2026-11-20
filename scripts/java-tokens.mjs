// Compare teaching annotations without ignoring changes inside strings or operators.
// Literals come first so comment markers inside them remain part of the program.
export function javaTokens(source) {
  const tokens = source.match(/"""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|\s+|[\p{L}\p{N}_$]+|>>>=|>>>|>>=|<<=|>>|<<|==|!=|<=|>=|&&|\|\||\+\+|--|\+=|-=|\*=|\/=|%=|&=|\|=|\^=|->|::|\.\.\.|[^\s]/gu) || [];
  return tokens.filter((token) => !/^\s|^\/\/|^\/\*/.test(token));
}

export function sameJavaCode(first, second) {
  return JSON.stringify(javaTokens(first)) === JSON.stringify(javaTokens(second));
}
