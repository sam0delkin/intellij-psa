import * as fs from 'fs';
import * as process from 'process';

const completions = [];
const notifications: any[] = [];
const elementFilter = [];

const contextString = fs.readFileSync(process.env.PSA_CONTEXT as string).toString();
const context = JSON.parse(contextString);
const language = process.env.PSA_LANGUAGE;
const type = process.env.PSA_TYPE;

if (language === 'JS') {
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
            elementFilter.push('JS:STRING_LITERAL');
        }
    }
}

console.log(JSON.stringify({completions, notifications, goto_element_filter: elementFilter}));
