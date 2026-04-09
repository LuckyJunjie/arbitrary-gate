# -*- coding: utf-8 -*-
def rn(r): return {chr(227)+chr(128)+chr(129):1,chr(227)+chr(129)+chr(154):2,chr(227)+chr(129)+chr(150):3,chr(227)+chr(129)+chr(153):4}.get(r,1)
def w(r): return {chr(227)+chr(128)+chr(129):60,chr(227)+chr(129)+chr(154):25,chr(227)+chr(129)+chr(150):12,chr(227)+chr(129)+chr(153):3}.get(r,60)
