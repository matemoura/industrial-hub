/**
 * Playwright global setup — generates test fixtures that require runtime logic.
 * Runs once before all specs.
 */
import * as fs from 'fs';
import * as path from 'path';
import * as zlib from 'zlib';

const FIXTURES_DIR = path.join(__dirname, 'fixtures');

export default async function globalSetup(): Promise<void> {
  fs.mkdirSync(FIXTURES_DIR, { recursive: true });
  generateValidImportXlsx();
}

/**
 * Creates a minimal but valid OOXML (.xlsx) file that the DynamicsExcelParser
 * can process successfully.  The workbook contains the 15 expected column
 * headers and two data rows (clock-in + one process record).
 *
 * Built without external dependencies — uses raw ZIP construction with Node's
 * built-in `zlib.deflateRawSync`.
 */
function generateValidImportXlsx(): void {
  const outPath = path.join(FIXTURES_DIR, 'valid-import.xlsx');
  // Re-generate only if missing to keep setup fast on re-runs.
  if (fs.existsSync(outPath)) return;

  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml"          ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml"     ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml"            ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>`;

  const relsRoot = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>`;

  const workbook = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>`;

  const workbookRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>`;

  // sharedStrings — all 15 header names + string cell values for the 2 data rows
  const strings = [
    'Trabalhador', 'Nome', 'Data do perfil', 'Hora inicial', 'Hora final',
    'Tipo de registro do diário', 'Referência', 'Nº oper.', 'Ident. do trabalho',
    'Descrição', 'Data inicial', 'Data final', 'Hora', 'Erro', 'Ident. do trabalho2',
    // data row strings
    'JANETE', 'Registro de entrada', 'Sistema', 'OPTR1', 'Não',
    'Processo', 'OP26000594', 'OPTR2', 'Montagem Fibra Laser',
  ];
  const sharedStrings = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.length}" uniqueCount="${strings.length}">
${strings.map((s) => `  <si><t>${escapeXml(s)}</t></si>`).join('\n')}
</sst>`;

  // Minimal styles with one date format (id=164) and one datetime format (id=165)
  const styles = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="yyyy-mm-dd"/>
    <numFmt numFmtId="165" formatCode="yyyy-mm-dd hh:mm:ss"/>
  </numFmts>
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0"   fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>`;

  // Excel serial date for 2026-03-15 = days since 1900-01-00 (Excel epoch)
  // 2026-03-15: years 1900→2026 = 126 years, serial ≈ 46125
  const dateSerial     = 46125;   // 2026-03-15
  const clockInStart   = dateSerial + (8 * 60) / 1440;         // 08:00
  const clockInEnd     = dateSerial + (8 * 60 + 1) / 1440;     // 08:01
  const processStart   = dateSerial + (8 * 60 + 1) / 1440;     // 08:01
  const processEnd     = dateSerial + (9 * 60 + 35) / 1440;    // 09:35

  // Helper: shared string index
  const si = (s: string) => strings.indexOf(s);

  // Row 1 — headers (all sharedString references)
  // Row 2 — clock-in record
  // Row 3 — process record
  const sheet = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>${si('Trabalhador')}</v></c>
      <c r="B1" t="s"><v>${si('Nome')}</v></c>
      <c r="C1" t="s"><v>${si('Data do perfil')}</v></c>
      <c r="D1" t="s"><v>${si('Hora inicial')}</v></c>
      <c r="E1" t="s"><v>${si('Hora final')}</v></c>
      <c r="F1" t="s"><v>${si('Tipo de registro do diário')}</v></c>
      <c r="G1" t="s"><v>${si('Referência')}</v></c>
      <c r="H1" t="s"><v>${si('Nº oper.')}</v></c>
      <c r="I1" t="s"><v>${si('Ident. do trabalho')}</v></c>
      <c r="J1" t="s"><v>${si('Descrição')}</v></c>
      <c r="K1" t="s"><v>${si('Data inicial')}</v></c>
      <c r="L1" t="s"><v>${si('Data final')}</v></c>
      <c r="M1" t="s"><v>${si('Hora')}</v></c>
      <c r="N1" t="s"><v>${si('Erro')}</v></c>
      <c r="O1" t="s"><v>${si('Ident. do trabalho2')}</v></c>
    </row>
    <row r="2">
      <c r="A2"><v>7</v></c>
      <c r="B2" t="s"><v>${si('JANETE')}</v></c>
      <c r="C2" s="1"><v>${dateSerial}</v></c>
      <c r="D2" s="2"><v>${clockInStart.toFixed(10)}</v></c>
      <c r="E2" s="2"><v>${clockInEnd.toFixed(10)}</v></c>
      <c r="F2" t="s"><v>${si('Registro de entrada')}</v></c>
      <c r="G2" t="s"><v>${si('Sistema')}</v></c>
      <c r="H2"><v>0</v></c>
      <c r="I2" t="s"><v>${si('OPTR1')}</v></c>
      <c r="J2" t="s"><v>${si('Registro de entrada')}</v></c>
      <c r="K2" s="1"><v>${dateSerial}</v></c>
      <c r="L2" s="1"><v>${dateSerial}</v></c>
      <c r="M2"><v>0</v></c>
      <c r="N2" t="s"><v>${si('Não')}</v></c>
      <c r="O2" t="s"><v>${si('OPTR1')}</v></c>
    </row>
    <row r="3">
      <c r="A3"><v>7</v></c>
      <c r="B3" t="s"><v>${si('JANETE')}</v></c>
      <c r="C3" s="1"><v>${dateSerial}</v></c>
      <c r="D3" s="2"><v>${processStart.toFixed(10)}</v></c>
      <c r="E3" s="2"><v>${processEnd.toFixed(10)}</v></c>
      <c r="F3" t="s"><v>${si('Processo')}</v></c>
      <c r="G3" t="s"><v>${si('OP26000594')}</v></c>
      <c r="H3"><v>10</v></c>
      <c r="I3" t="s"><v>${si('OPTR2')}</v></c>
      <c r="J3" t="s"><v>${si('Montagem Fibra Laser')}</v></c>
      <c r="K3" s="1"><v>${dateSerial}</v></c>
      <c r="L3" s="1"><v>${dateSerial}</v></c>
      <c r="M3"><v>1.57</v></c>
      <c r="N3" t="s"><v>${si('Não')}</v></c>
      <c r="O3" t="s"><v>${si('OPTR2')}</v></c>
    </row>
  </sheetData>
</worksheet>`;

  const entries: Array<{ name: string; data: Buffer }> = [
    { name: '[Content_Types].xml',              data: Buffer.from(contentTypes, 'utf8') },
    { name: '_rels/.rels',                      data: Buffer.from(relsRoot, 'utf8') },
    { name: 'xl/workbook.xml',                  data: Buffer.from(workbook, 'utf8') },
    { name: 'xl/_rels/workbook.xml.rels',        data: Buffer.from(workbookRels, 'utf8') },
    { name: 'xl/sharedStrings.xml',             data: Buffer.from(sharedStrings, 'utf8') },
    { name: 'xl/styles.xml',                    data: Buffer.from(styles, 'utf8') },
    { name: 'xl/worksheets/sheet1.xml',         data: Buffer.from(sheet, 'utf8') },
  ];

  fs.writeFileSync(outPath, buildZip(entries));
}

function escapeXml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

// ─── Minimal ZIP builder ──────────────────────────────────────────────────────
// PKZIP local file header + deflate compression for each entry, then central
// directory + EOCD.  Enough for Office Open XML validation.
function buildZip(entries: Array<{ name: string; data: Buffer }>): Buffer {
  const parts: Buffer[] = [];
  const cdEntries: Array<{ name: Buffer; offset: number; crc: number; compSize: number; origSize: number }> = [];

  for (const entry of entries) {
    const nameBytes   = Buffer.from(entry.name, 'utf8');
    const compressed  = zlib.deflateRawSync(entry.data, { level: 6 });
    const crc         = crc32(entry.data);
    const offset      = parts.reduce((s, b) => s + b.length, 0);

    // Local file header
    const lfh = Buffer.alloc(30 + nameBytes.length);
    lfh.writeUInt32LE(0x04034b50, 0);   // signature
    lfh.writeUInt16LE(20, 4);            // version needed
    lfh.writeUInt16LE(0, 6);             // general purpose bit flag
    lfh.writeUInt16LE(8, 8);             // compression method: deflate
    lfh.writeUInt16LE(0, 10);            // mod time
    lfh.writeUInt16LE(0, 12);            // mod date
    lfh.writeUInt32LE(crc >>> 0, 14);
    lfh.writeUInt32LE(compressed.length, 18);
    lfh.writeUInt32LE(entry.data.length, 22);
    lfh.writeUInt16LE(nameBytes.length, 26);
    lfh.writeUInt16LE(0, 28);            // extra field length
    nameBytes.copy(lfh, 30);

    parts.push(lfh, compressed);
    cdEntries.push({ name: nameBytes, offset, crc, compSize: compressed.length, origSize: entry.data.length });
  }

  const cdOffset = parts.reduce((s, b) => s + b.length, 0);

  // Central directory
  for (const e of cdEntries) {
    const cde = Buffer.alloc(46 + e.name.length);
    cde.writeUInt32LE(0x02014b50, 0);  // signature
    cde.writeUInt16LE(20, 4);           // version made by
    cde.writeUInt16LE(20, 6);           // version needed
    cde.writeUInt16LE(0, 8);
    cde.writeUInt16LE(8, 10);           // deflate
    cde.writeUInt16LE(0, 12);
    cde.writeUInt16LE(0, 14);
    cde.writeUInt32LE(e.crc >>> 0, 16);
    cde.writeUInt32LE(e.compSize, 20);
    cde.writeUInt32LE(e.origSize, 24);
    cde.writeUInt16LE(e.name.length, 28);
    cde.writeUInt16LE(0, 30);           // extra
    cde.writeUInt16LE(0, 32);           // comment
    cde.writeUInt16LE(0, 34);           // disk start
    cde.writeUInt16LE(0, 36);           // internal attr
    cde.writeUInt32LE(0, 38);           // external attr
    cde.writeUInt32LE(e.offset, 42);
    e.name.copy(cde, 46);
    parts.push(cde);
  }

  const cdSize   = parts.reduce((s, b) => s + b.length, 0) - cdOffset;
  const eocd     = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(cdEntries.length, 8);
  eocd.writeUInt16LE(cdEntries.length, 10);
  eocd.writeUInt32LE(cdSize, 12);
  eocd.writeUInt32LE(cdOffset, 16);
  eocd.writeUInt16LE(0, 20);
  parts.push(eocd);

  return Buffer.concat(parts);
}

function crc32(buf: Buffer): number {
  let crc = 0xffffffff;
  const table = makeCrcTable();
  for (let i = 0; i < buf.length; i++) {
    crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff];
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function makeCrcTable(): Uint32Array {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    t[n] = c;
  }
  return t;
}
