<html>
<head>
<title th:text="${title}">Webpage Template</title>
</head>
<body>
<h1 th:text="${title}">Header level 1</h1>

<ul>
    <li th:each="link: ${links}"><a href="unknown.html" th:href="${link.URL}" th:text="${link.title}">Title</a></li>
</ul>
</body>
</html>
