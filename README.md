# HCA messaging client

A GUI tool to send templated messages through hca-bot, commissioned by HCA Hospice Care.

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
HCA_CLIENT_TOKEN=$(cat hcaClientToken) electron .
```

## Release

```
echo CLIENT_TOKEN_FOR_BOT > hcaClientToken
yarn run build && electron-packager . --platform=win32 --arch=x64 --asar --extra-resource=hcaClientToken
```

Test build on linux

```
yarn run build && electron-packager . --arch=x64 --asar --extra-resource=hcaClientToken
```
