# basex-webapp

This is a slightly tweaked version of the BaseX Database Admin web app to allow acces to non-admin users.
The current version is based on [BaseX v12.1](https://github.com/BaseXdb/basex/tree/12.1/basex-api/src/main/webapp).

You can view the changes applied by looking at the diff between the `upstream-webapp` branch and a version-specific branch,
e.g. https://github.com/mblink/basex-webapp/compare/upstream-webapp...webapp-12.1

## Setup

To setup the repository for development and updating (more details below), run the following commands:

```bash
git clone git@github.com:mblink/basex-webapp.git
cd basex-webapp
git remote add upstream git@github.com:BaseXdb/basex
```

## Updating

To update to a newer version of BaseX:

1. Run `bash +x update-version.sh <old-version> <new-version>`
    1. Note: the versions you pass should match [tags in the BaseX repo](https://github.com/BaseXdb/basex/tags)
2. Follow the instructions printed at the end of the script to:
    1. Resolve any conflicts
    2. Complete the merge
    3. Push the new version's branch
