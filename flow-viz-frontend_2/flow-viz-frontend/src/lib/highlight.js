const KEYWORDS = ("abstract assert boolean break byte case catch char class const continue default do double else " +
  "enum extends final finally float for goto if implements import instanceof int interface long native new package " +
  "private protected public return short static strictfp super switch synchronized this throw throws transient try " +
  "void volatile while var record sealed permits yield null true false").split(" ");

// order matters: comments and strings must win over keywords inside them
const PARTS = [
  /\/\*[\s\S]*?\*\//,
  /\/\/[^\n]*/,
  /"(?:\\.|[^"\\\n])*"/,
  /'(?:\\.|[^'\\\n])'/,
  /@[A-Za-z_][\w.]*/,
  new RegExp("\\b(?:" + KEYWORDS.join("|") + ")\\b"),
  /\b\d[\d_]*(?:\.\d+)?[LlFfDd]?\b/,
];
const CLASS_BY_GROUP = [null, "comment", "comment", "string", "string", "annotation", "keyword", "number"];

/** Splits Java source into lines of {text, cls} tokens. Joining a line's tokens gives back the original line. */
export function highlightJava(source) {
  const token = new RegExp(PARTS.map((r) => "(" + r.source + ")").join("|"), "g");
  const lines = [[]];

  const push = (text, cls) => {
    text.split("\n").forEach((part, i) => {
      if (i > 0) lines.push([]);
      if (part) lines[lines.length - 1].push({ text: part, cls });
    });
  };

  let last = 0;
  let match;
  while ((match = token.exec(source)) !== null) {
    if (match.index > last) push(source.slice(last, match.index), null);
    const group = match.slice(1).findIndex((g) => g !== undefined) + 1;
    push(match[0], CLASS_BY_GROUP[group]);
    last = match.index + match[0].length;
  }
  if (last < source.length) push(source.slice(last), null);
  return lines;
}
