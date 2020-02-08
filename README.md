# Mass WhatsApp

A GUI tool to send templated WhatsApp messages in bulk, commissioned by HCA Hospice Care.

Built with ClojureScript, Shadow-cljs, Electron and Reagent, based off https://github.com/ahonn/shadow-electron-starter.

## Setup

```
yarn
```

## Development

Start shadow-cljs to auto-compile on file save

```
yarn run dev
```

Start GUI app

```
electron .
```

## Release

```
yarn run build
electron-packager . --platform=win32 --arch=x64
```
