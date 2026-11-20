// Compare teaching annotations without ignoring changes inside strings or operators.
// Literals come first so comment markers inside them remain part of the program.
export function javaTokens(source) {
  const tokens = source.match(/"""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|\s+|[\p{L}\p{N}_$]+|>>>=|>>>|>>=|<<=|>>|<<|==|!=|<=|>=|&&|\|\||\+\+|--|\+=|-=|\*=|\/=|%=|&=|\|=|\^=|->|::|\.\.\.|[^\s]/gu) || [];
  return tokens.filter((token) => !/^\s|^\/\/|^\/\*/.test(token));
}

export function sameJavaCode(first, second) {
  return JSON.stringify(javaTokens(first)) === JSON.stringify(javaTokens(second));
}

// A commented copy has its own public class name so both versions can compile
// in the same Maven project. All other program tokens must remain identical.
export function sameJavaCodeIgnoringClassName(original, annotated) {
  const first = javaTokens(original);
  const second = javaTokens(annotated);
  const declaration = first.findIndex((token, index) => token === 'class' && first[index - 1] === 'public');
  if (declaration < 0 || second[declaration] !== 'class' || second[declaration - 1] !== 'public') return false;
  second[declaration + 1] = first[declaration + 1];
  return JSON.stringify(first) === JSON.stringify(second);
}
