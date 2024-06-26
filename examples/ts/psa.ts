import * as fs from 'fs';
import * as process from 'process';
import { generateMyAwesomeTemplate } from './templates/tsClass.template'

const completions = [];
const notifications: any[] = [];

const type = process.env.PSA_TYPE;

if (type === 'Info') {
  console.log(JSON.stringify({
    supported_languages: ["TypeScript"],
    goto_element_filter: ["JS:STRING_LITERAL"],
    templates: [
      {
        type: "single_file",
        name: "my_awesome_template",
        title: "My Awesome Template",
        path_regex: "^\/src\/[^\/]+\/$",
        fields: [
          {
            name: "className",
            title: "Class Name",
            type: "Text",
            options: []
          },
          {
            name: "abstract",
            title: "Is Abstract",
            type: "Checkbox",
            options: []
          },
          {
            name: "comment",
            title: "Comment",
            type: "Select",
            options: ["OptionA", "OptionB", "OptionC"]
          },
          {
            name: "richText",
            title: "Rich Text with Completion",
            type: "RichText",
            options: ['Completion A', 'Completion B', 'Completion C']
          },
          {
            name: "collection",
            title: "Collection of text fields",
            type: "Collection",
            options: []
          }
        ]
      }
    ]
  }));

  process.exit(0)
}

const contextString = fs.readFileSync(process.env.PSA_CONTEXT as string).toString();
const context = JSON.parse(contextString);

if (type === 'GenerateFileFromTemplate') {
    console.log(JSON.stringify({
        'file_name': context['formFields']['className'] + '.class.ts',
        'content': generateMyAwesomeTemplate(context['formFields']),
        'form_fields': {
          richText: {
            options: ['Completion A', 'Completion B', 'Completion C']
          }
        }
    }));

    process.exit(0)
}

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
