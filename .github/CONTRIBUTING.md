# Contributing to Quillnote



## 🌎 Translations

### Supported languages

| Language     | Translator    | Status   |
|--------------|---------------|----------|
| English      | [@msoultanidis](https://github.com/msoultanidis) | Complete |
| Greek (`el`) | [@msoultanidis](https://github.com/msoultanidis) | Complete |
| Polish (`pl`) | [@TheDidek](https://github.com/TheDidek) | Complete |
| Brazilian Portuguese (`pt-rBR`) | [@RodolfoCandido](https://github.com/RodolfoCandido) | Complete |
| Italian (`it`) | [@danigarau](https://github.com/danigarau) | Complete |
| French (`fr`) | [@locness3](https://github.com/locness3) | Complete |
| Spanish (`es`) | [@urizev](https://github.com/urizev) | Complete |

You can help Quillnote grow by translating it in languages it does not support yet or by improving existing translations.

### How to translate

1. Fork the repository.
2. Create a new branch from the `develop` branch. Give it a good name, like `translation-FR` for a French translation.
3. Inside `app/src/main/res` create a folder named `values-COUNTRY_CODE` where `COUNTRY_CODE` is the code for the language you're translating Quillnote in. For example, the Greek translation lies inside the `values-el` folder.
4. Copy `app/src/main/res/values/strings.xml` inside the folder you just created.
5. Edit the file by translating the strings between the XML tags and not the tags themselves. For example, in this line `<string name="nav_all_notes">All Notes</string>` you should only translate `All Notes`.
6. When done, commit your changes.
7. Finally, create a new pull request [here](https://github.com/msoultanidis/quillnote/pulls).

**Thanks for your interest in contributing to Quillnote!**
