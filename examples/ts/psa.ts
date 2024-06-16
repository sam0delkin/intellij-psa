import * as fs from 'fs';
import * as process from 'process';

const completions = [];
const notifications: any[] = [];

const type = process.env.PSA_TYPE;

if (type === 'Info') {
  console.log(JSON.stringify({
    supported_languages: ["TypeScript"],
    goto_element_filter: ["JS:STRING_LITERAL"],
  }));

  process.exit(0)
}

const contextString = fs.readFileSync(process.env.PSA_CONTEXT as string).toString();
const context = JSON.parse(contextString);
const language = process.env.PSA_LANGUAGE;

if (language === 'TypeScript') {
  if (type === 'Completion') {
    if (context['elementType'] === 'JS:STRING_LITERAL') {
      completions.push({
        text: 'My Completion',
        bold: false,
        priority: 123,
        type: 'MyType',
      });
    }
  }
  if (type === 'GoTo') {
    if (context['elementType'] === 'JS:STRING_LITERAL') {
      completions.push({
        link: '/examples/ts/psa.ts:0:0',
      });
    }
  }
}

console.log(JSON.stringify({completions, notifications}));
