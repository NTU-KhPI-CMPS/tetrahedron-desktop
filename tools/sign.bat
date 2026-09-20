@echo off

set version=7.1.0

call cd ..

call signtool sign /v /debug /fd SHA256 /tr http://timestamp.acs.microsoft.com /td SHA256 ^
    /dlib "C:\Program Files\signtool\Azure.CodeSigning.Dlib.dll" /dmdf tools\signing.json dist\Tetrahedron-%version%.msi