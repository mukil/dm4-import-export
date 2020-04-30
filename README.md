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

A DMX 5.0-beta-7 installation
https://github.com/jri/deepamehta#readme


Installation
------------

1. Download the DMX Import/Export plugin:

2. Move the DMX Import/Export plugin to the `dmx/bundle-deploy` folder.

3. Restart DMX.

Research
--------

- Improve exported SVG Document about using Defs:
  [http://www.w3.org/TR/SVG11/struct.html#DefsElement](http://www.w3.org/TR/SVG11/struct.html#DefsElement)
- Test improved text export (e.g. with Inkscape) about textArea:
  [http://www.w3.org/TR/SVGTiny12/text.html#TextAreaElement](http://www.w3.org/TR/SVGTiny12/text.html#TextAreaElement)
- Investigate if wheter [.graphml](https://en.wikipedia.org/wiki/GraphML) (e.g. for yEd) or `.mm` (used in Freemind) is another valuable export format. I currently assume that `.mm` is to restricted to house topic maps (cause mind maps usually have _one_ central topic not many) and one topicmap would translate to many mind maps (one for each "cluster" in the topic map).

Licensing
---------

DMX Import Export is available freely under the GNU Affero General Public License, version 3.

All third party components incorporated into the DMX Import Export Software are licensed under the original license provided by the owner of the applicable component.

Version History
---------------

**0.8.0** Upcoming

* Adapted Bookmarks import to be compatible with DMX 5.0-beta-7
* Adapted Topicmaps import to be compatible with DMX 5.0-beta-7
* Import process integrated into the [dmx-upload-dialog](https://github.com/mukil/dmx-upload-dialog) module
* Adapted package names and changed License to AGPL

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


