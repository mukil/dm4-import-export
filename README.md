DMX Import Export
=================

A DMX plugin to

- Export single DMX Topicmaps (JSON)
- Import single DMX Topicmaps (JSON)
- Import Firefox Bookmarks Backup Files (JSON)
- Import Chrome/Chromium Bookmarks Backups (HTML)

Usage
-----
Next to the Topicmap dropdown menu:

- Export: Trigger the "Export Topicmap" command if you want to save the current Topicmap as JSON file
- Import: Trigger the "Upload Dialog" command and select your file to import. You will be guided through the import process.

Requirements
------------

Java 8+ and DMX 5.0
https://github.com/jri/deepamehta#readme

Installation
------------

1. Download the DMX Import/Export plugin:

2. Move the DMX Import/Export plugin to the `dmx/bundle-deploy` folder.

Licensing
---------

DMX Import Export is available freely under the GNU Affero General Public License, version 3.

All third party components incorporated into the DMX Import Export Software are licensed under the original license provided by the owner of the applicable component:

- [jsoup HTML parser](http://jsoup.org/), (C) 2009 - 2020 Jonathan Hedley, MIT Licensed

Version History
---------------

**0.9.0** Aug 16, 2020

* Adapted to latest core API changes in DMX 5.0
* Runs with `per_workspace=true` platform filerepo configuration
* Added attribution to Jonathan Hedley for `jsoup-1.10.2`

**0.8.0** June 19, 2020

* Adapted Bookmarks import to be compatible with DMX 5.0-beta-7
* Adapted Topicmaps import to be compatible with DMX 5.0-beta-7
* Import process integrated into the [dmx-upload-dialog](https://github.com/mukil/dmx-upload-dialog) module
* Adapted package names and changed License to AGPL 3.0

**0.6**, Apr 27, 2019

- Improved support for importing Firefox (v53.x) Bookmarks (folders are imported as tags)
- Added dependency to dm4-tags-1.4.0 module
- Added support for importing Chromium (v53.x) Bookmark backup files
- Added support for Zotero Bookmarks (v4.x) from HTML Report
- Compatible with DeepaMehta 4.9.2

**0.5**, 22 Jun 2016

- Added support for importing bookmarks based on _Firefox Bookmark Backup_ documents (.json).
- Compatible with DeepaMehta 4.8

**0.3.1**, 11. December 2015

- Re-introduced proprietary import and export of Topicmaps (JSON based)
- After exporting a Topicmap to SVG the exported document is rendered immediately

**0.3**, 21. November 2015

- More robust SVG Topicmap export (fallback for icons unavailable)
- Limitted functionality from within the Topicmap menu (SVG Export only)
- Compatible with DM 4.7 (and the new file repository mechanism)

**0.2**, 21. July 2014

- Was never really released

**0.1** -- May 26, 2014


Authors:
--------

Malte Reißig, (C) 2015-2020 [mikromedia.de](http://www.mikromedia.de)<br/>
Carolina Garcia, (C) 2014 [abriraqui.net](http://www.abriraqui.net)


