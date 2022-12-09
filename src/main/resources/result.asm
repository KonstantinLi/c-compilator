.586
.model flat, c
option casemap:none
include masm32\include\user32.inc
include masm32\include\kernel32.inc
include masm32\include\windows.inc
include masm32\include\msvcrt.inc

includelib masm32\lib\kernel32.lib
includelib masm32\lib\user32.lib
includelib masm32\lib\msvcrt.lib

.const
	message1 db "Unknown ASCII code", 0
	message2 db "Upper case character must be given", 0
	message3 db "Upper case: %c -> lower case: %c", 0


.data
	lower_case DD ?
	upper_case DD ?


.code
to_lower_case proc
push ebp
mov ebp, esp
mov EAX, [ebp+8]
cmp EAX, 41h
JGE @to_lower_case0
@to_lower_case4:
cmp EAX, 0
JGE @to_lower_case2
@to_lower_case5:
mov eax, 0
pop ebp
ret
@to_lower_case2:
cmp EAX, 127
JLE @to_lower_case3
JMP @to_lower_case5
@to_lower_case3:
pop ebp
ret
@to_lower_case0:
cmp EAX, 5Ah
JLE @to_lower_case1
JMP @to_lower_case4
@to_lower_case1:
mov lower_case, EAX
ADD lower_case, 32
mov eax, lower_case
pop ebp
ret
to_lower_case endp


main:
mov upper_case, 'q'
push upper_case
call to_lower_case
mov lower_case, eax
cmp lower_case, 0
JE @main0
mov EAX, upper_case
cmp lower_case, EAX
JE @main1
invoke crt_printf, ADDR message3, upper_case, lower_case
jmp @end
@main1:
invoke crt_printf, ADDR message2
jmp @end
@main0:
invoke crt_printf, ADDR message1
jmp @end
@end:
invoke ExitProcess, 0
end main

