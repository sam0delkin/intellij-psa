const fs = require('fs');
const process = require('process');

const completions = [];
const notifications = [];

const type = process.env.PSA_TYPE;

if (type === 'Info') {
  console.log(JSON.stringify({
    supported_languages: ["JavaScript"],
    goto_element_filter: ["JS:STRING_LITERAL"],
  }));

  process.exit(0)
}

const contextString = fs.readFileSync(process.env.PSA_CONTEXT).toString();
const context = JSON.parse(contextString);
const language = process.env.PSA_LANGUAGE;

if (language === 'JavaScript') {
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
        link: '/examples/js/psa.js:0:0',
      });
    }
  }
}

console.log(JSON.stringify({completions, notifications}));
