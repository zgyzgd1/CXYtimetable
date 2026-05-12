(function () {
  const SOURCE = "hebau-urp";
  const SOURCE_KIND = "hebau-dom";
  const SOURCE_KIND_WITH_TEXT = "hebau-dom-text";
  const SOURCE_KIND_TEXT_ONLY = "hebau-text";
  const MAX_PLAIN_TEXT_CHARS = 100000;
  const SECTION_TIMES = [
    { section: 1, start: "08:00", end: "08:45" },
    { section: 2, start: "08:55", end: "09:40" },
    { section: 3, start: "10:10", end: "10:55" },
    { section: 4, start: "11:05", end: "11:50" },
    { section: 5, start: "14:30", end: "15:15" },
    { section: 6, start: "15:25", end: "16:10" },
    { section: 7, start: "16:20", end: "17:05" },
    { section: 8, start: "17:15", end: "18:00" },
    { section: 9, start: "18:30", end: "19:15" },
    { section: 10, start: "19:25", end: "20:10" },
    { section: 11, start: "20:20", end: "21:05" },
    { section: 12, start: "21:15", end: "22:00" }
  ];
  const TEACHER_ATTRS = ["data-jsmc", "data-jsxm", "data-teacher", "data-teachers", "teacher", "teachers", "jsmc", "jsxm"];
  const LOCATION_ATTRS = ["data-jxdd", "data-cdmc", "data-room", "data-classroom", "data-jxcdmc", "data-skdd", "data-dd", "jxdd", "cdmc", "room"];
  const COURSE_CLASS_ATTRS = ["data-jxbmc", "data-jxb", "data-bjmc", "data-class", "data-class-name", "data-teaching-class", "jxbmc", "bjmc"];

  function getBridge() {
    return window.JwBridge || null;
  }

  function postError(message) {
    const bridge = getBridge();
    if (bridge && bridge.postError) {
      bridge.postError(String(message));
    }
  }

  function clean(text) {
    return String(text || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t\r\n]+/g, " ")
      .trim();
  }

  function joinClean(parts) {
    const seen = [];
    parts.forEach(function (part) {
      const value = clean(part);
      if (value && seen.indexOf(value) < 0) seen.push(value);
    });
    return clean(seen.join(" "));
  }

  function textOf(element) {
    return element ? joinClean([element.innerText, element.textContent]) : "";
  }

  function normalizePlainText(text) {
    return String(text || "")
      .replace(/\u00a0/g, " ")
      .replace(/[ \t\r]+/g, " ")
      .split("\n")
      .map(function (line) { return clean(line); })
      .filter(Boolean)
      .join("\n");
  }

  function collectPlainText() {
    return collectDocuments()
      .map(function (doc) {
        return normalizePlainText(doc.body ? (doc.body.innerText || doc.body.textContent || "") : "");
      })
      .filter(Boolean)
      .join("\n\n")
      .slice(0, MAX_PLAIN_TEXT_CHARS);
  }

  function attributeTextOf(element) {
    if (!element || !element.attributes) return "";
    const importantNames = [
      "title",
      "data-title",
      "data-original-title",
      "data-content",
      "data-qtip",
      "aria-label",
      "onclick",
      "data-kcmc",
      "data-jsmc",
      "data-jsxm",
      "data-jxdd",
      "data-sksj",
      "data-zc",
      "data-xqj",
      "data-ksjc",
      "data-jsjc",
      "data-jxbmc",
      "data-jxb",
      "data-bjmc",
      "data-cdmc",
      "data-room",
      "data-classroom",
      "data-jxcdmc",
      "data-skdd"
    ];
    const parts = [];
    Array.prototype.forEach.call(element.attributes, function (attribute) {
      const name = attribute.name.toLowerCase();
      const value = clean(attribute.value);
      if (!value) return;
      if (importantNames.indexOf(name) >= 0 || (name.indexOf("data-") === 0 && /课程|教师|老师|教室|地点|星期|周|节|\d{1,2}:\d{2}/.test(value))) {
        parts.push(value);
      }
    });
    return joinClean(parts);
  }

  function attributeValueOf(element, names) {
    if (!element || !element.attributes) return "";
    const wanted = names.map(function (name) { return name.toLowerCase(); });
    for (const attribute of Array.prototype.slice.call(element.attributes)) {
      if (wanted.indexOf(attribute.name.toLowerCase()) >= 0) {
        const value = clean(attribute.value);
        if (value) return value;
      }
    }
    return "";
  }

  function contextAttributeValue(item, root, names) {
    let node = item;
    while (node && node !== document.body) {
      const value = attributeValueOf(node, names);
      if (value) return value;
      if (node === root) break;
      node = node.parentElement;
    }
    return "";
  }

  function richTextOf(element) {
    return joinClean([textOf(element), attributeTextOf(element)]);
  }

  function hasCourseTiming(text) {
    return /周次|起止周|上课周|星期|周[一二三四五六日天]|\d{1,2}\s*[-~～至到—–,，、]\s*\d{1,2}\s*(?:节|周)|\d{1,2}:\d{2}/.test(clean(text));
  }

  function courseContextText(item, cell) {
    const ownText = richTextOf(item);
    let node = item;
    while (node && node !== cell && node !== document.body) {
      const nodeText = richTextOf(node);
      if (hasCourseTiming(nodeText)) return joinClean([ownText, nodeText]);
      node = node.parentElement;
    }
    return joinClean([ownText, richTextOf(cell)]);
  }

  function collectDocuments() {
    const docs = [];
    const seen = [];

    function visit(doc, depth) {
      if (!doc || depth > 4 || seen.indexOf(doc) >= 0) return;
      seen.push(doc);
      docs.push(doc);
      Array.prototype.forEach.call(doc.querySelectorAll("iframe, frame"), function (frame) {
        try {
          visit(frame.contentDocument || (frame.contentWindow && frame.contentWindow.document), depth + 1);
        } catch (ignored) {
          // Cross-origin frames are intentionally ignored.
        }
      });
    }

    visit(document, 0);
    return docs;
  }

  function semesterNameFromText(text) {
    const match = clean(text).match(/20\d{2}\s*[-–—]\s*20\d{2}\s*(?:学年)?\s*(?:第?\s*[一二12]\s*学期)?/);
    return match ? clean(match[0]) : "";
  }

  function parseDay(text) {
    const value = clean(text);
    const patterns = [
      [/星期一|周一|礼拜一|Mon(?:day)?/i, 1],
      [/星期二|周二|礼拜二|Tue(?:sday)?/i, 2],
      [/星期三|周三|礼拜三|Wed(?:nesday)?/i, 3],
      [/星期四|周四|礼拜四|Thu(?:rsday)?/i, 4],
      [/星期五|周五|礼拜五|Fri(?:day)?/i, 5],
      [/星期六|周六|礼拜六|Sat(?:urday)?/i, 6],
      [/星期日|星期天|周日|周天|礼拜日|礼拜天|Sun(?:day)?/i, 7]
    ];
    for (const item of patterns) {
      if (item[0].test(value)) return item[1];
    }
    const numeric = value.match(/(?:星期|周|礼拜)?\s*([1-7一二三四五六日天])(?:\s|$)/);
    if (!numeric) return null;
    return { "1": 1, "一": 1, "2": 2, "二": 2, "3": 3, "三": 3, "4": 4, "四": 4, "5": 5, "五": 5, "6": 6, "六": 6, "7": 7, "日": 7, "天": 7 }[numeric[1]] || null;
  }

  function parseSections(text) {
    const value = clean(text);
    const listMatch = value.match(/(?:第)?\s*((?:\d{1,2}\s*[,，、]\s*)+\d{1,2})\s*(?:节|小节|课时)/);
    let start;
    let end;
    if (listMatch) {
      const numbers = listMatch[1].split(/[,，、]/).map(function (part) { return Number(clean(part)); }).filter(Boolean);
      start = numbers[0];
      end = numbers[numbers.length - 1];
    } else {
      const match = value.match(/(?:第)?\s*(\d{1,2})\s*(?:[-~～至到—–]\s*(\d{1,2}))?\s*(?:节|小节|课时)/);
      if (match) {
        start = Number(match[1]);
        end = Number(match[2] || match[1]);
      } else {
        const clockSections = parseSectionsByClock(value);
        if (clockSections) return clockSections;
      }
    }
    if (!start || !end || end < start) return null;
    return { startSection: start, endSection: end };
  }

  function minutesOf(value) {
    const match = clean(value).match(/^(\d{1,2}):(\d{2})$/);
    if (!match) return null;
    return Number(match[1]) * 60 + Number(match[2]);
  }

  function parseSectionsByClock(text) {
    const value = clean(text);
    const match = value.match(/(\d{1,2}:\d{2})\s*[-~～至到—–]\s*(\d{1,2}:\d{2})/);
    if (!match) return null;
    const startMinutes = minutesOf(match[1]);
    const endMinutes = minutesOf(match[2]);
    if (startMinutes == null || endMinutes == null || endMinutes <= startMinutes) return null;
    const start = SECTION_TIMES.find(function (time) { return minutesOf(time.start) === startMinutes; });
    const end = SECTION_TIMES.find(function (time) { return minutesOf(time.end) === endMinutes; });
    if (!start || !end || end.section < start.section) return null;
    return { startSection: start.section, endSection: end.section };
  }

  function addWeeksFromFragment(fragment, oddOnly, evenOnly, weeks) {
    const value = clean(fragment)
      .replace(/[，、；;]/g, ",")
      .replace(/[－—–~～至到]/g, "-")
      .replace(/第/g, "")
      .replace(/周/g, "")
      .replace(/\s+/g, "");
    if (!value) return;
    value.split(",").forEach(function (part) {
      const range = part.match(/^(\d{1,2})-(\d{1,2})$/);
      const single = part.match(/^(\d{1,2})$/);
      if (range) {
        const start = Number(range[1]);
        const end = Number(range[2]);
        if (start > 0 && end >= start && end <= 30) {
          for (let week = start; week <= end; week += 1) weeks.add(week);
        }
      } else if (single) {
        const week = Number(single[1]);
        if (week > 0 && week <= 30) weeks.add(week);
      }
    });
    Array.from(weeks).forEach(function (week) {
      if ((oddOnly && week % 2 === 0) || (evenOnly && week % 2 === 1)) weeks.delete(week);
    });
  }

  function expandWeeks(text) {
    const value = clean(text)
      .replace(/[，、]/g, ",")
      .replace(/[－—–~～至到]/g, "-")
      .replace(/\s+/g, " ");
    const oddOnly = /单周|单/.test(value);
    const evenOnly = /双周|双/.test(value);
    const weeks = new Set();
    let match;

    const explicitWeekPattern = /第?\s*([0-9,\-\s]+)\s*周/g;
    while ((match = explicitWeekPattern.exec(value)) !== null) {
      addWeeksFromFragment(match[1], oddOnly, evenOnly, weeks);
    }

    const bracketWeekPattern = /([0-9,\-\s]+)\s*[\(（]\s*周\s*[\)）]/g;
    while ((match = bracketWeekPattern.exec(value)) !== null) {
      addWeeksFromFragment(match[1], oddOnly, evenOnly, weeks);
    }

    const labeledWeekPattern = /(?:周次|周数|起止周|上课周次|上课周)\s*[:：]?\s*([0-9,\-\s]+)/g;
    while ((match = labeledWeekPattern.exec(value)) !== null) {
      addWeeksFromFragment(match[1], oddOnly, evenOnly, weeks);
    }

    if (weeks.size === 0 && /^[0-9,\-\s]+$/.test(value) && value.length <= 40) {
      addWeeksFromFragment(value, oddOnly, evenOnly, weeks);
    }

    return Array.from(weeks)
      .sort(function (a, b) { return a - b; });
  }

  function directParts(element) {
    return Array.prototype.map.call(element.childNodes, function (node) {
      return clean(node.nodeType === Node.TEXT_NODE ? node.data : node.textContent);
    }).filter(Boolean);
  }

  function fieldBoundaryPattern() {
    return "授课教师|任课教师|教师姓名|主讲教师|教师|老师|教学班名称|教学班|课程班级|行政班|上课班级|班级|教学地点|上课地点|地点|教室|校区|周次|周数|起止周|上课周次|上课周|星期|周[一二三四五六日天]|第?\\d{1,2}\\s*[-~～至到—–,，、]\\s*\\d{1,2}\\s*(?:周|节)|\\d{1,2}:\\d{2}";
  }

  function cleanupFieldValue(text) {
    return clean(text)
      .replace(/^[,，、；;\s:：]+/, "")
      .replace(/[,，、；;\s:：]+$/, "");
  }

  function parseLabeledValue(text, labels) {
    const value = clean(text);
    for (const label of labels) {
      const regex = new RegExp("(?:^|[\\s,，、；;])" + label + "\\s*[:：]?\\s*(.*?)(?=\\s*(?:" + fieldBoundaryPattern() + ")\\s*[:：]?|$)");
      const match = value.match(regex);
      const parsed = match ? cleanupFieldValue(match[1]) : "";
      if (parsed) return parsed;
    }
    return "";
  }

  function isCourseClassToken(text) {
    return /教学班|课程班级|行政班|上课班级|班级|专业|年级|[0-9]{2,}\s*[-~～至到—–]\s*[0-9]{2,}/.test(clean(text));
  }

  function isLocationToken(text) {
    return /校区|教学楼|教室|实验|机房|楼|馆|室|厅|[A-Za-z]?\d{2,}/.test(clean(text));
  }

  function stripKnownCourseName(text, courseName) {
    const value = clean(text);
    const name = clean(courseName);
    return name && value.indexOf(name) === 0 ? clean(value.slice(name.length)) : value;
  }

  function parseTeacher(text, item, root, courseName) {
    const attr = contextAttributeValue(item, root, TEACHER_ATTRS);
    if (attr) return cleanupFieldValue(attr);
    const value = clean(text);
    const labeled = parseLabeledValue(value, ["授课教师", "任课教师", "教师姓名", "主讲教师", "教师", "老师"]);
    if (labeled) return labeled;
    const part = directParts(item || {}).map(clean).find(function (candidate) {
      if (!candidate || candidate === courseName) return false;
      if (hasCourseTiming(candidate) || isLocationToken(candidate) || isCourseClassToken(candidate)) return false;
      return /老师|教授|讲师|^[\u4e00-\u9fa5·]{2,8}(?:[、/][\u4e00-\u9fa5·]{2,8})*$|^[A-Za-z][A-Za-z .'-]{1,30}$/.test(candidate);
    });
    if (part) return cleanupFieldValue(part.replace(/老师$/, ""));
    const remaining = stripKnownCourseName(value, courseName);
    const beforeWeek = remaining.match(/^([^,，、；;\s]+)\s+(?=\d{1,2}\s*[-~～至到—–,，、]\s*\d{1,2}\s*周|第?\d{1,2}\s*[-~～至到—–,，、]\s*\d{1,2}\s*节)/);
    if (beforeWeek && !isLocationToken(beforeWeek[1]) && !isCourseClassToken(beforeWeek[1])) {
      return cleanupFieldValue(beforeWeek[1].replace(/老师$/, ""));
    }
    return "";
  }

  function parsePosition(text, item, root) {
    const attr = contextAttributeValue(item, root, LOCATION_ATTRS);
    if (attr) return cleanupFieldValue(attr);
    const labeled = parseLabeledValue(clean(text), ["教学地点", "上课地点", "地点", "教室"]);
    if (labeled) return labeled;
    const tokens = clean(text)
      .split(/[,，、；;]/)
      .map(clean)
      .filter(Boolean)
      .filter(function (token) {
        return !/课程|周|教师|老师|星期|周[一二三四五六日天]|\d{1,2}\s*[-~～至到—–,，、]\s*\d{1,2}\s*节/.test(token) && !isCourseClassToken(token);
      });
    const likely = tokens.filter(function (token) {
      return isLocationToken(token);
    });
    return likely.length ? likely[likely.length - 1] : (tokens.length ? tokens[tokens.length - 1] : "");
  }

  function parseCourseClass(text, item, root) {
    const attr = contextAttributeValue(item, root, COURSE_CLASS_ATTRS);
    if (attr) return cleanupFieldValue(attr);
    const labeled = parseLabeledValue(clean(text), ["教学班名称", "教学班", "课程班级", "行政班", "上课班级", "班级"]);
    if (labeled) return labeled;
    const part = directParts(item || {}).map(clean).find(function (candidate) {
      return candidate && isCourseClassToken(candidate) && !hasCourseTiming(candidate) && !isLocationToken(candidate);
    });
    return part ? cleanupFieldValue(part.replace(/^(?:教学班名称|教学班|课程班级|行政班|上课班级|班级)\s*[:：]?/, "")) : "";
  }

  function buildRemark(courseClass, existingRemark) {
    return clean(existingRemark);
  }

  function parseCourseName(item, contextText) {
    const attrName = clean(item.getAttribute("data-kcmc") || item.getAttribute("data-name") || item.getAttribute("course-name") || "");
    if (attrName) return attrName;
    const parts = directParts(item).filter(function (part) {
      return part && !hasCourseTiming(part) && !/教师|老师|地点|教室|校区|教学楼/.test(part);
    });
    if (parts.length) return parts[0];
    return clean((item.getAttribute("title") || contextText).split(/教师|老师|地点|教室|周次|第?\d{1,2}\s*[-~～至到—–,，、]\s*\d{1,2}\s*(?:周|节)/)[0]);
  }

  // HEBAU XiaoAI adapter parses .wut_container by row(section) and column(day).
  // This path follows that structure, while avoiding brittle childNode indexes.
  function parseWutContainers(doc) {
    const courses = [];
    Array.prototype.forEach.call(doc.querySelectorAll(".wut_container"), function (container) {
      const rows = Array.prototype.slice.call(container.querySelectorAll("tbody tr"));
      if (rows.length === 0) return;
      const hasHeaderRow = /星期|周一|周二|周三|周四|周五|周六|周日/.test(textOf(rows[0])) || rows[0].querySelectorAll("th").length > 0;

      rows.forEach(function (row, rowIndex) {
        if (hasHeaderRow && rowIndex === 0) return;
        const cells = Array.prototype.slice.call(row.children);
        const rowSections = cells.length > 0 ? parseSections(textOf(cells[0])) : null;
        const inferredSection = rowSections ? rowSections.startSection : (hasHeaderRow ? rowIndex : rowIndex + 1);
        cells.forEach(function (cell, cellIndex) {
          if (cellIndex === 0) return;
          Array.prototype.forEach.call(cell.querySelectorAll("div.mtt_item_kcmc, .mtt_item_kcmc"), function (item) {
            const contextText = courseContextText(item, cell);
            const name = parseCourseName(item, contextText);
            const teacher = parseTeacher(contextText, item, cell, name);
            const position = parsePosition(contextText, item, cell);
            const courseClass = parseCourseClass(contextText, item, cell);
            const day = parseDay(contextText) || Math.min(cellIndex, 7);
            const sections = parseSections(contextText) || rowSections || {
              startSection: inferredSection,
              endSection: inferredSection + 1
            };
            const weeks = expandWeeks(contextText);
            if (!name || weeks.length === 0) return;
            courses.push({
              name: name,
              teacher: teacher,
              position: position,
              courseClass: courseClass,
              day: day,
              startSection: sections.startSection,
              endSection: sections.endSection,
              weeks: weeks,
              remark: buildRemark(courseClass, "")
            });
          });
        });
      });
    });
    return courses;
  }

  function parseCourseNodes(doc) {
    const courses = [];
    Array.prototype.forEach.call(doc.querySelectorAll(".mtt_item_kcmc, [data-kcmc]"), function (item) {
      if (item.closest && item.closest(".wut_container")) return;
      const contextRoot = item.closest ? (item.closest("td, li, tr, .mtt_item, .course, .kbcontent") || item.parentElement || item) : item;
      const contextText = courseContextText(item, contextRoot);
      const day = parseDay(contextText);
      const sections = parseSections(contextText);
      const weeks = expandWeeks(contextText);
      const name = parseCourseName(item, contextText);
      const teacher = parseTeacher(contextText, item, contextRoot, name);
      const position = parsePosition(contextText, item, contextRoot);
      const courseClass = parseCourseClass(contextText, item, contextRoot);
      if (!name || !day || !sections || weeks.length === 0) return;
      courses.push({
        name: name,
        teacher: teacher,
        position: position,
        courseClass: courseClass,
        day: day,
        startSection: sections.startSection,
        endSection: sections.endSection,
        weeks: weeks,
        remark: buildRemark(courseClass, "")
      });
    });
    return courses;
  }

  function headerIndexes(table) {
    const rows = Array.prototype.slice.call(table.querySelectorAll("tr"));
    const headerRow = rows.find(function (row) {
      return row.querySelectorAll("th").length > 0 || /课程|教师|老师|地点|教室|周次|星期|节/.test(textOf(row));
    });
    const cells = headerRow ? Array.prototype.slice.call(headerRow.children).map(textOf) : [];
    function findIndex(pattern) {
      return cells.findIndex(function (cell) { return pattern.test(cell); });
    }
    return {
      name: findIndex(/课程名称|课程名|课程|名称/),
      teacher: findIndex(/教师|老师/),
      position: findIndex(/地点|教室|校区/),
      courseClass: findIndex(/教学班|课程班级|行政班|上课班级|班级/),
      day: findIndex(/星期|周几/),
      section: findIndex(/节|节次|时间/),
      weeks: findIndex(/周次|周数|起止周|周/)
    };
  }

  function cellByIndex(cells, index) {
    return index >= 0 && index < cells.length ? cells[index] : "";
  }

  function guessName(cells) {
    return cells.find(function (cell) {
      return cell.length > 1 && !parseDay(cell) && !parseSections(cell) && expandWeeks(cell).length === 0;
    }) || "";
  }

  function parseTable(table) {
    const indexes = headerIndexes(table);
    const rows = Array.prototype.slice.call(table.querySelectorAll("tbody tr"));
    const fallbackRows = rows.length ? rows : Array.prototype.slice.call(table.querySelectorAll("tr")).slice(1);
    return fallbackRows.map(function (row) {
      const cells = Array.prototype.slice.call(row.children).map(textOf).filter(Boolean);
      if (cells.length < 3) return null;
      const rowText = cells.join(" ");
      const day = parseDay(cellByIndex(cells, indexes.day) || rowText);
      const sections = parseSections(cellByIndex(cells, indexes.section) || rowText);
      const weeks = expandWeeks(cellByIndex(cells, indexes.weeks) || rowText);
      const name = cellByIndex(cells, indexes.name) || guessName(cells);
      const courseClass = cellByIndex(cells, indexes.courseClass) || parseCourseClass(rowText, row, row);
      if (!name || !day || !sections || weeks.length === 0) return null;
      return {
        name: name,
        teacher: cellByIndex(cells, indexes.teacher) || parseTeacher(rowText, row, row, name),
        position: cellByIndex(cells, indexes.position) || parsePosition(rowText, row, row),
        courseClass: courseClass,
        day: day,
        startSection: sections.startSection,
        endSection: sections.endSection,
        weeks: weeks,
        remark: buildRemark(courseClass, "")
      };
    }).filter(Boolean);
  }

  function collectCourses() {
    const courses = [];
    const docs = collectDocuments();
    docs.forEach(function (doc) {
      courses.push.apply(courses, parseWutContainers(doc));
      courses.push.apply(courses, parseCourseNodes(doc));
      const scopedTables = Array.prototype.slice.call(doc.querySelectorAll("table"));
      scopedTables.forEach(function (table) {
        const tableText = textOf(table);
        if (!/课程|教师|老师|地点|教室|周次|星期|节/.test(tableText)) return;
        courses.push.apply(courses, parseTable(table));
      });
    });
    const byKey = new Map();
    courses.forEach(function (course) {
      const key = [
        course.name,
        course.teacher,
        course.position,
        course.day,
        course.startSection,
        course.endSection,
        course.weeks.join(",")
      ].join("|").toLowerCase();
      byKey.set(key, course);
    });
    return Array.from(byKey.values());
  }

  try {
    const courses = collectCourses();
    const plainText = collectPlainText();
    if (courses.length === 0 && !plainText) {
      postError("未在当前页面识别到课程表。请确认已进入学期课表页面，或尝试切换电脑/手机模式后重试。");
      return;
    }
    const bridge = getBridge();
    if (!bridge || !bridge.postCourses) return;
    const payload = {
      source: SOURCE,
      sourceKind: courses.length === 0 ? SOURCE_KIND_TEXT_ONLY : SOURCE_KIND_WITH_TEXT,
      semesterName: semesterNameFromText(plainText),
      semesterStartDate: null,
      courses: courses,
      sectionTimes: SECTION_TIMES,
      plainText: plainText
    };
    bridge.postCourses(JSON.stringify(payload));
  } catch (error) {
    postError(error && error.message ? error.message : String(error));
  }
}());
